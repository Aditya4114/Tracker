package com.tracker.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ParsingService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> GENERIC_DOMAINS = new HashSet<>(Arrays.asList(
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com", "aol.com", "protonmail.com",
            "greenhouse.io", "lever.co", "workday.com", "myworkday.com", "myworkdayjobs.com", "ashbyhq.com",
            "smartrecruiters.com", "icims.com", "taleo.net", "bamboohr.com", "jobvite.com", "breezy.hr",
            "linkedin.com", "indeed.com", "hirevue.com", "rippling.com", "workable.com"
    ));

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BayesianClassifierService classifierService;

    public ParsingService() {}

    public ParsingService(BayesianClassifierService classifierService) {
        this.classifierService = classifierService;
    }

    public Map<String, String> parseEmail(String subject, String plainTextBody) {
        return parseEmail(subject, plainTextBody, "");
    }

    public Map<String, String> parseEmail(String subject, String plainTextBody, String sender) {
        String safeSubject = subject != null ? subject : "";
        String safeBody = plainTextBody != null ? plainTextBody : "";
        String safeSender = sender != null ? sender : "";

        Map<String, String> extractedData = extractUsingRegex(safeSubject, safeBody, safeSender);

        // 1. Classification check via BayesianClassifierService if available
        if (!isLikelyJobEmail(safeSubject, safeBody, safeSender)) {
            extractedData.put("isJobEmail", "false");
            return extractedData;
        }
        extractedData.put("isJobEmail", "true");

        // 2. Cost-Optimization Gate:
        // If company and status are determined with confidence, skip Gemini API entirely!
        String company = extractedData.get("company");
        if (company != null && !company.isBlank() && !"Not Available".equalsIgnoreCase(company)) {
            return extractedData;
        }

        // 3. Gemini API Fallback (Only for ambiguous / edge-case emails)
        String truncatedBody = safeBody.length() > 4000 ? safeBody.substring(0, 4000) : safeBody;
        Map<String, String> geminiData = callGeminiFallback(safeSubject, truncatedBody, safeSender, extractedData);

        if (geminiData != null) {
            geminiData.put("isJobEmail", "true");
            return geminiData;
        }
        return extractedData;
    }

    public Map<String, String> extractUsingRegex(String subject, String body, String sender) {
        Map<String, String> data = new HashMap<>();
        data.put("company", "Not Available");
        data.put("position", "Not Available");
        data.put("jobId", "Not Available");
        data.put("status", extractStatus(subject, body));

        // Try Job ID extraction first
        String jobId = extractJobId(subject, body);
        if (jobId != null) {
            data.put("jobId", jobId);
        }

        // Tier 1: ATS / Platform Specific Parsers
        boolean foundByAts = tryAtsParsing(subject, body, sender, data);
        if (foundByAts && isValidCompany(data.get("company"))) {
            cleanExtractedData(data);
            return data;
        }

        // Tier 2: General High-Precision Regex (Subject & Body)
        tryGeneralRegexParsing(subject, body, data);
        if (isValidCompany(data.get("company"))) {
            cleanExtractedData(data);
            return data;
        }

        // Tier 3: Sender Name & Domain Heuristics
        String companyFromSender = extractCompanyFromSender(sender);
        if (isValidCompany(companyFromSender)) {
            data.put("company", companyFromSender);
        }

        cleanExtractedData(data);
        return data;
    }

    private boolean isLikelyJobEmail(String subject, String body, String sender) {
        if (classifierService != null) {
            BayesianClassifierService.ClassificationResult result =
                    classifierService.classify(subject, body.length() > 500 ? body.substring(0, 500) : body, sender);
            return result != BayesianClassifierService.ClassificationResult.SPAM;
        }

        // Fallback if classifierService not injected
        String combined = ((subject != null ? subject : "") + " " + (body != null ? body : "") + " " + (sender != null ? sender : "")).toLowerCase();
        return combined.contains("application") || combined.contains("applied") || combined.contains("interview") ||
               combined.contains("candidate") || combined.contains("requisition") || combined.contains("job") ||
               combined.contains("career") || combined.contains("offer") || combined.contains("recruiting") ||
               combined.contains("talent") || combined.contains("hire") || combined.contains("position") ||
               combined.contains("greenhouse") || combined.contains("lever.co") || combined.contains("workday");
    }

    private String extractStatus(String subject, String body) {
        String text = (subject + " " + body).toLowerCase();

        // 1. Offer
        if (text.contains("offer letter") || text.contains("job offer") || text.contains("pleased to offer you") ||
            text.contains("congratulations on your offer") || text.contains("offer of employment")) {
            return "Offer";
        }

        // 2. Interview / Assessment
        if (text.contains("interview") || text.contains("invite you to") || text.contains("phone screen") ||
            text.contains("technical screen") || text.contains("coding assessment") || text.contains("hackerrank") ||
            text.contains("codesignal") || text.contains("assessment invitation") || text.contains("schedule a time") ||
            text.contains("schedule your") || text.contains("next round") || text.contains("take-home")) {
            return "Interview";
        }

        // 3. Rejection
        if (text.contains("unfortunately") || text.contains("not moving forward") || text.contains("pursue other candidates") ||
            text.contains("decided not to move forward") || text.contains("regret to inform") || text.contains("other applicants") ||
            text.contains("will not be moving forward") || text.contains("position has been filled") || text.contains("not selected") ||
            text.contains("rejection") || text.contains("unable to offer you") || text.contains("not be able to move forward")) {
            return "Rejected";
        }

        // 4. In Progress / Under Review
        if (text.contains("under review") || text.contains("reviewing your application") || text.contains("in progress")) {
            return "In Progress";
        }

        // Default to Applied
        return "Applied";
    }

    private String extractJobId(String subject, String body) {
        Pattern pattern = Pattern.compile("(?i)(?:Job\\s*(?:ID|#)|Req(?:uisition)?\\s*(?:ID|#)?|Reference\\s*(?:Number|ID|#))[:\\s#]*([A-Za-z0-9_-]{4,25})");
        Matcher m = pattern.matcher(subject);
        if (m.find()) {
            return m.group(1).trim();
        }
        m = pattern.matcher(body);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private boolean tryAtsParsing(String subject, String body, String sender, Map<String, String> data) {
        String lowerSender = sender.toLowerCase();
        String lowerSubject = subject.toLowerCase();

        // 1. LinkedIn
        if (lowerSender.contains("linkedin.com") || lowerSubject.contains("linkedin")) {
            // Pattern: Your application for [Position] at [Company] was sent
            Pattern p1 = Pattern.compile("(?i)application for\\s+(.+?)\\s+at\\s+(.+?)(?:\\s+was sent|\\.|$)");
            Matcher m = p1.matcher(subject);
            if (m.find()) {
                data.put("position", cleanText(m.group(1)));
                data.put("company", cleanCompanyName(m.group(2)));
                return true;
            }

            // Pattern: Your application to [Company] for [Position]
            Pattern p2 = Pattern.compile("(?i)application to\\s+(.+?)\\s+for\\s+(.+?)(?:\\s+was sent|\\.|$)");
            m = p2.matcher(subject);
            if (m.find()) {
                data.put("company", cleanCompanyName(m.group(1)));
                data.put("position", cleanText(m.group(2)));
                return true;
            }

            // Pattern: Your application was sent to [Company]
            Pattern p3 = Pattern.compile("(?i)application was sent to\\s+(.+?)(?:\\.|$)");
            m = p3.matcher(subject);
            if (m.find()) {
                data.put("company", cleanCompanyName(m.group(1)));
                return true;
            }

            // Pattern: [User], your application was submitted to [Company]
            Pattern p4 = Pattern.compile("(?i)application was submitted to\\s+(.+?)(?:\\.|$)");
            m = p4.matcher(subject);
            if (m.find()) {
                data.put("company", cleanCompanyName(m.group(1)));
                return true;
            }
        }

        // 2. Indeed
        if (lowerSender.contains("indeed.com") || lowerSubject.contains("indeed application")) {
            Pattern pSubj = Pattern.compile("(?i)Indeed Application:\\s*(.+?)(?:\\s*-\\s*(.+))?$");
            Matcher mSubj = pSubj.matcher(subject);
            if (mSubj.find()) {
                String first = cleanText(mSubj.group(1));
                String second = mSubj.group(2) != null ? cleanText(mSubj.group(2)) : null;
                if (second != null && !second.isBlank()) {
                    data.put("position", first);
                    data.put("company", cleanCompanyName(second));
                    return true;
                } else {
                    data.put("position", first);
                }
            }
            if ("Not Available".equals(data.get("company"))) {
                Pattern pBody = Pattern.compile("(?i)Application submitted\\s+([^\\n]+)\\s+([^\\n-]+)");
                Matcher mBody = pBody.matcher(body);
                if (mBody.find()) {
                    if ("Not Available".equals(data.get("position"))) data.put("position", cleanText(mBody.group(1)));
                    data.put("company", cleanCompanyName(mBody.group(2)));
                    return true;
                }
            }
        }

        // 3. Greenhouse
        if (lowerSender.contains("greenhouse.io") || lowerSender.contains("greenhouse-mail.io") || body.toLowerCase().contains("greenhouse.io")) {
            Pattern pSubj = Pattern.compile("(?i)Thank you for applying to\\s+(.+?)(?:\\.|!|$)");
            Matcher m = pSubj.matcher(subject);
            if (m.find()) {
                data.put("company", cleanCompanyName(m.group(1)));
                extractPositionFromBody(body, data);
                return true;
            }

            Pattern pBody = Pattern.compile("(?i)Thank you for applying to(?: the)?\\s+(.+?)(?:\\s+position|\\s+role)?\\s+at\\s+([A-Za-z0-9\\s&.,'-]+?)(?:\\.|!|\n|$)");
            m = pBody.matcher(body);
            if (m.find()) {
                data.put("position", cleanText(m.group(1)));
                data.put("company", cleanCompanyName(m.group(2)));
                return true;
            }
        }

        // 4. Lever
        if (lowerSender.contains("lever.co") || body.toLowerCase().contains("lever.co")) {
            Pattern pSubj = Pattern.compile("(?i)Thank you for (?:applying to|your interest in)\\s+(.+?)(?:\\.|!|$)");
            Matcher m = pSubj.matcher(subject);
            if (m.find()) {
                data.put("company", cleanCompanyName(m.group(1)));
                extractPositionFromBody(body, data);
                return true;
            }
        }

        // 5. Workday
        if (lowerSender.contains("myworkday.com") || lowerSender.contains("workday.com") || lowerSender.contains("myworkdayjobs.com")) {
            Pattern pSubj = Pattern.compile("(?i)(?:Thank you for your interest in|Application Submitted:?|Thank you for applying to)\\s+([A-Za-z0-9\\s&.,'-]+?)(?:\\.|!|$)");
            Matcher m = pSubj.matcher(subject);
            if (m.find()) {
                data.put("company", cleanCompanyName(m.group(1)));
                extractPositionFromBody(body, data);
                return true;
            }
        }

        // 6. Ashby / SmartRecruiters / iCIMS
        if (lowerSender.contains("ashbyhq.com") || lowerSender.contains("smartrecruiters.com") || lowerSender.contains("icims.com")) {
            Pattern p = Pattern.compile("(?i)(?:Thank you for applying to|Application to|Your application to)\\s+([A-Za-z0-9\\s&.,'-]+?)(?:\\.|!|$)");
            Matcher m = p.matcher(subject);
            if (m.find()) {
                data.put("company", cleanCompanyName(m.group(1)));
                extractPositionFromBody(body, data);
                return true;
            }
        }

        return false;
    }

    private void tryGeneralRegexParsing(String subject, String body, Map<String, String> data) {
        String cleanSubject = subject.replaceAll("\\s*\\([^)]*\\)", " ").trim();

        // Pattern: Application for [Position] received
        Pattern p5 = Pattern.compile("(?i)(?:application for|candidature for the position of)\\s+([A-Za-z0-9\\s/&#+.\\-()]+?)\\s+received");
        Matcher m = p5.matcher(cleanSubject);
        if (m.find() && "Not Available".equals(data.get("position"))) {
            data.put("position", cleanText(m.group(1)));
        }

        // Pattern: Receipt of your [Company] job application
        Pattern p6 = Pattern.compile("(?i)Receipt of your\\s+([A-Za-z0-9\\s&.,'-]+?)\\s+job application");
        m = p6.matcher(cleanSubject);
        if (m.find()) {
            data.put("company", cleanCompanyName(m.group(1)));
        }

        // Pattern: recent job application for / application as [Position]
        Pattern p7 = Pattern.compile("(?i)(?:recent job application for|application as|application for the role of)\\s+([A-Za-z0-9\\s/&#+.\\-()]+?)(?:\\s*[-–—|]|$)");
        m = p7.matcher(cleanSubject);
        if (m.find() && "Not Available".equals(data.get("position"))) {
            data.put("position", cleanText(m.group(1)));
        }

        // Pattern: Application to [Company] for [Position] OR Application for [Position] at [Company]
        Pattern p2 = Pattern.compile("(?i)application (?:for|to)\\s+([A-Za-z0-9\\s&.,'-]+?)\\s+(?:at|for|as)\\s+([A-Za-z0-9\\s/&#+.\\-()]+?)(?:\\s*[-–—|]|$|\\.)");
        m = p2.matcher(cleanSubject);
        if (m.find()) {
            String first = cleanText(m.group(1));
            String second = cleanText(m.group(2));
            if (cleanSubject.toLowerCase().contains("at " + second.toLowerCase())) {
                if ("Not Available".equals(data.get("position"))) data.put("position", first);
                if ("Not Available".equals(data.get("company"))) data.put("company", cleanCompanyName(second));
            } else {
                if ("Not Available".equals(data.get("company"))) data.put("company", cleanCompanyName(first));
                if ("Not Available".equals(data.get("position"))) data.put("position", second);
            }
        }

        // Pattern: Thank you for applying to [Company] - [Position]
        Pattern p1 = Pattern.compile("(?i)(?:Thank you for applying to|Thanks for applying to|Thank you for your application to)\\s+([A-Za-z0-9\\s&.,'-]+?)(?:\\s*[-–—|:]\\s*(.+))?$");
        m = p1.matcher(cleanSubject);
        if (m.find()) {
            if ("Not Available".equals(data.get("company"))) data.put("company", cleanCompanyName(m.group(1)));
            if (m.group(2) != null && !m.group(2).isBlank() && "Not Available".equals(data.get("position"))) {
                data.put("position", cleanText(m.group(2)));
            }
        }

        // Pattern: Your application (?:to|at|with) [Company]
        Pattern p3 = Pattern.compile("(?i)application (?:to|at|with)\\s+([A-Za-z0-9\\s&.,'-]+?)(?:\\s*[-–—|:]|$|\\.)");
        m = p3.matcher(cleanSubject);
        if (m.find() && "Not Available".equals(data.get("company"))) {
            data.put("company", cleanCompanyName(m.group(1)));
        }

        // Pattern: [Company] Job Application / [Company] Application Received
        Pattern p4 = Pattern.compile("(?i)^([A-Za-z0-9\\s&.,'-]+?)\\s+(?:Job Application|Application Received|Careers|Recruiting|Interview)");
        m = p4.matcher(cleanSubject);
        if (m.find() && "Not Available".equals(data.get("company"))) {
            String candidate = cleanCompanyName(m.group(1));
            if (isValidCompany(candidate) && !candidate.equalsIgnoreCase("Your") && !candidate.equalsIgnoreCase("New")) {
                data.put("company", candidate);
            }
        }

        // Try extracting from body if subject didn't give company
        Pattern pBody = Pattern.compile("(?i)(?:thank you for applying to|thank you for your application to|your application to)\\s+([A-Za-z0-9\\s&.,'-]+?)(?:\\s+for\\s+(?:the\\s+)?([A-Za-z0-9\\s/&#+.\\-()]+?)(?:\\s+position|\\s+role)?)?(?:\\.|!|\n|,)");
        m = pBody.matcher(body);
        if (m.find()) {
            if ("Not Available".equals(data.get("company"))) data.put("company", cleanCompanyName(m.group(1)));
            if (m.group(2) != null && !m.group(2).isBlank() && "Not Available".equals(data.get("position"))) {
                data.put("position", cleanText(m.group(2)));
            }
        }
    }

    private void extractPositionFromBody(String body, Map<String, String> data) {
        if (!"Not Available".equals(data.get("position"))) return;

        Pattern p = Pattern.compile("(?i)(?:for the|for a|position of|role of|applying to the|applying for the|applied to the|applied for the|application for(?: the)?|application as)\\s+([A-Za-z0-9\\s/&#+.\\-()]+?)(?:\\s+(?:role|position|opportunity|at|with)|\\(|\\.|\\n)");
        Matcher m = p.matcher(body);
        if (m.find()) {
            String pos = cleanText(m.group(1));
            if (pos.length() >= 3 && pos.length() <= 60 && !pos.equalsIgnoreCase("the")) {
                data.put("position", pos);
            }
        }
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s*\\([^)]*\\)", " ")
                   .replaceAll("^[\"']+|[\"']+$", "")
                   .replaceAll("^[\\-–—:|.,!]+|[\\-–—:|.,!]+$", "")
                   .trim();
    }

    private String cleanCompanyName(String raw) {
        if (raw == null) return "";
        String cleaned = cleanText(raw);
        // Strip suffixes like " - Interview Invitation", " - Status Update", " - Confirmation"
        cleaned = cleaned.replaceAll("(?i)\\s*[-–—|:]\\s*(?:interview\\s*(?:invitation|request)?|status\\s*update|application\\s*(?:received|submitted|update|confirmation)|next\\s*steps|update).*$", "").trim();
        return cleaned;
    }

    private String extractCompanyFromSender(String sender) {
        if (sender == null || sender.isBlank()) return null;

        // E.g. "Google Careers <careers-noreply@google.com>" or "Stripe <jobs@stripe.com>"
        String displayName = "";
        String email = "";

        if (sender.contains("<") && sender.contains(">")) {
            displayName = sender.substring(0, sender.indexOf("<")).trim();
            email = sender.substring(sender.indexOf("<") + 1, sender.indexOf(">")).trim();
        } else if (sender.contains("@")) {
            email = sender.trim();
        } else {
            displayName = sender.trim();
        }

        // Clean display name
        if (!displayName.isBlank()) {
            String cleaned = displayName.replaceAll("(?i)[\"']", "").trim();
            cleaned = cleaned.replaceAll("(?i)\\b(careers?|recruiting|recruitment|talent(\\s+team)?|jobs?|hiring|team|hr|no-?reply|notifications?|alerts?|support|via\\s+linkedin)\\b", "").trim();
            cleaned = cleaned.replaceAll("(?i)^[\\s\\-–—:|]+|[\\s\\-–—:|]+$", "").trim();
            if (isValidCompany(cleaned)) {
                return cleaned;
            }
        }

        // Check email domain
        if (email.contains("@")) {
            String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
            if (!GENERIC_DOMAINS.contains(domain)) {
                String namePart = domain.contains(".") ? domain.substring(0, domain.indexOf(".")) : domain;
                if (namePart.length() >= 2 && !namePart.equalsIgnoreCase("mail") && !namePart.equalsIgnoreCase("email")) {
                    return Character.toUpperCase(namePart.charAt(0)) + namePart.substring(1);
                }
            }
        }

        return null;
    }

    private boolean isValidCompany(String company) {
        if (company == null) return false;
        String trimmed = company.trim();
        if (trimmed.length() < 2 || trimmed.length() > 60) return false;
        String lower = trimmed.toLowerCase();
        return !lower.equals("not available") && !lower.equals("your application") && !lower.equals("thank you") &&
               !lower.equals("application") && !lower.equals("update") && !lower.equals("status") &&
               !lower.equals("careers") && !lower.equals("jobs");
    }

    private void cleanExtractedData(Map<String, String> data) {
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String val = entry.getValue();
            if (val != null) {
                val = val.replaceAll("^[\"']+|[\"']+$", "").trim();
                val = val.replaceAll("^[\\-–—:|.,!]+|[\\-–—:|.,!]+$", "").trim();
                if (val.isBlank()) {
                    val = "Not Available";
                }
                entry.setValue(val);
            } else {
                entry.setValue("Not Available");
            }
        }
    }

    private Map<String, String> callGeminiFallback(String subject, String body, String sender, Map<String, String> regexData) {
        try {
            String prompt = "Extract job application details from the following email. " +
                    "Return ONLY a JSON object with keys: company, position, jobId, status. " +
                    "If a value cannot be found, use 'Not Available'. " +
                    "Current extracted status hint: " + regexData.get("status") + "\n\n" +
                    "Sender: " + sender + "\n" +
                    "Subject: " + subject + "\n" +
                    "Body: " + body;

            com.fasterxml.jackson.databind.node.ObjectNode partNode = objectMapper.createObjectNode();
            partNode.put("text", prompt);

            com.fasterxml.jackson.databind.node.ArrayNode partsArray = objectMapper.createArrayNode();
            partsArray.add(partNode);

            com.fasterxml.jackson.databind.node.ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.set("parts", partsArray);

            com.fasterxml.jackson.databind.node.ArrayNode contentsArray = objectMapper.createArrayNode();
            contentsArray.add(contentNode);

            com.fasterxml.jackson.databind.node.ObjectNode rootRequest = objectMapper.createObjectNode();
            rootRequest.set("contents", contentsArray);

            String requestBody = objectMapper.writeValueAsString(rootRequest);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", geminiApiKey);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(geminiApiUrl, entity, String.class);

            if (response == null) return null;

            JsonNode root = objectMapper.readTree(response);
            String textResponse = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            
            textResponse = textResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            
            JsonNode geminiJson = objectMapper.readTree(textResponse);
            
            Map<String, String> result = new HashMap<>();
            result.put("company", geminiJson.path("company").asText("Not Available"));
            result.put("position", geminiJson.path("position").asText("Not Available"));
            result.put("jobId", geminiJson.path("jobId").asText("Not Available"));
            result.put("status", geminiJson.path("status").asText(regexData.get("status")));
            
            cleanExtractedData(result);
            return result;
        } catch (Exception e) {
            System.err.println("Gemini API fallback triggered error: " + e.getMessage());
            return null;
        }
    }
}
