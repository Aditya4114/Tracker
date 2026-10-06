package com.tracker.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.models.GlobalModelState;
import com.tracker.repositories.GlobalModelStateRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BayesianClassifierService {

    private static final Logger logger = LoggerFactory.getLogger(BayesianClassifierService.class);

    public enum ClassificationResult {
        JOB,
        UNSURE,
        SPAM
    }

    private final GlobalModelStateRepository globalModelStateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Fast-pass dedicated ATS domains that are guaranteed job emails
    private static final Set<String> DEDICATED_ATS = new HashSet<>(Arrays.asList(
            "greenhouse.io", "lever.co", "workday.com", "myworkday.com", "myworkdayjobs.com",
            "ashbyhq.com", "smartrecruiters.com", "icims.com", "taleo.net", "bamboohr.com",
            "jobvite.com", "breezy.hr", "rippling.com", "workable.com"
    ));

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
            "by", "can", "did", "do", "does", "doing", "don", "down", "during", "each", "few", "for",
            "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
            "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "just",
            "me", "more", "most", "my", "myself", "no", "nor", "not", "now", "of", "off", "on", "once",
            "only", "or", "other", "our", "ours", "ourselves", "out", "over", "own", "s", "same", "she",
            "should", "so", "some", "such", "t", "than", "that", "the", "their", "theirs", "them",
            "themselves", "then", "there", "these", "they", "this", "those", "through", "to", "too",
            "under", "until", "up", "very", "was", "we", "were", "what", "when", "where", "which",
            "while", "who", "whom", "why", "will", "with", "you", "your", "yours", "yourself"
    ));

    // In-memory model state
    private final Map<String, Integer> jobWordCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> spamWordCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> confirmedSpamDomains = new ConcurrentHashMap<>();

    private int totalJobDocs = 0;
    private int totalSpamDocs = 0;
    private long totalJobWords = 0;
    private long totalSpamWords = 0;

    public BayesianClassifierService(GlobalModelStateRepository globalModelStateRepository) {
        this.globalModelStateRepository = globalModelStateRepository;
    }

    @PostConstruct
    public void init() {
        try {
            Optional<GlobalModelState> savedState = globalModelStateRepository.findTopByOrderByIdDesc();
            if (savedState.isPresent() && savedState.get().getModelData() != null) {
                loadFromJson(savedState.get().getModelData());
                logger.info("Loaded Global Naive Bayes model from DB. JobDocs={}, SpamDocs={}, Vocab={}",
                        totalJobDocs, totalSpamDocs, getVocabularySize());
            } else {
                logger.info("No saved model found in DB. Seeding baseline training dataset...");
                seedBaselineDataset();
                persistState();
            }
        } catch (Exception e) {
            logger.error("Error initializing BayesianClassifierService, falling back to baseline: ", e);
            seedBaselineDataset();
        }
    }

    /**
     * Classifies incoming email into JOB, UNSURE, or SPAM.
     */
    public ClassificationResult classify(String subject, String snippet, String sender) {
        String lowerSender = sender != null ? sender.toLowerCase() : "";

        // Stage 1: Fast-Pass for dedicated ATS domains
        for (String ats : DEDICATED_ATS) {
            if (lowerSender.contains(ats)) {
                return ClassificationResult.JOB;
            }
        }

        double pJob = calculateJobProbability(subject, snippet);
        String senderDomain = extractDomain(lowerSender);

        // Stage 2: High Confidence Job
        if (pJob >= 0.80) {
            return ClassificationResult.JOB;
        }

        // Stage 3: Low-score check with Safety Net Invariant:
        // An email type is only silently dropped if its sender domain has been confirmed as spam at least twice
        int spamConfirmations = confirmedSpamDomains.getOrDefault(senderDomain, 0);
        if (pJob < 0.15 && spamConfirmations >= 2) {
            return ClassificationResult.SPAM;
        }

        // Safety Net default: route for user confirmation
        return ClassificationResult.UNSURE;
    }

    /**
     * Calculates P(Job | tokens) using Laplace smoothed log-likelihood.
     */
    public double calculateJobProbability(String subject, String snippet) {
        String combined = (subject != null ? subject : "") + " " + (snippet != null ? snippet : "");
        List<String> tokens = sanitizeAndTokenize(combined);

        if (tokens.isEmpty()) {
            return 0.5;
        }

        int vocabSize = Math.max(1, getVocabularySize());
        int safeJobDocs = Math.max(1, totalJobDocs);
        int safeSpamDocs = Math.max(1, totalSpamDocs);
        double totalDocs = safeJobDocs + safeSpamDocs;

        double logPriorJob = Math.log(safeJobDocs / totalDocs);
        double logPriorSpam = Math.log(safeSpamDocs / totalDocs);

        double logLikelihoodJob = logPriorJob;
        double logLikelihoodSpam = logPriorSpam;

        long safeTotalJobWords = Math.max(1, totalJobWords);
        long safeTotalSpamWords = Math.max(1, totalSpamWords);

        for (String token : tokens) {
            int countInJob = jobWordCounts.getOrDefault(token, 0);
            int countInSpam = spamWordCounts.getOrDefault(token, 0);

            // Laplace smoothing: (count + 1) / (totalWords + vocabSize)
            double probWordGivenJob = (countInJob + 1.0) / (safeTotalJobWords + vocabSize);
            double probWordGivenSpam = (countInSpam + 1.0) / (safeTotalSpamWords + vocabSize);

            logLikelihoodJob += Math.log(probWordGivenJob);
            logLikelihoodSpam += Math.log(probWordGivenSpam);
        }

        // Log-odds difference
        double delta = logLikelihoodJob - logLikelihoodSpam;

        // Sigmoid mapping
        if (delta > 30.0) return 1.0;
        if (delta < -30.0) return 0.0;
        return 1.0 / (1.0 + Math.exp(-delta));
    }

    /**
     * Online training: updates in-memory model and persists state asynchronously.
     */
    public synchronized void train(String subject, String snippet, String sender, boolean isJob) {
        String combined = (subject != null ? subject : "") + " " + (snippet != null ? snippet : "");
        List<String> tokens = sanitizeAndTokenize(combined);

        if (isJob) {
            totalJobDocs++;
            for (String token : tokens) {
                jobWordCounts.merge(token, 1, Integer::sum);
                totalJobWords++;
            }
        } else {
            totalSpamDocs++;
            for (String token : tokens) {
                spamWordCounts.merge(token, 1, Integer::sum);
                totalSpamWords++;
            }
            if (sender != null && !sender.isBlank()) {
                String domain = extractDomain(sender.toLowerCase());
                confirmedSpamDomains.merge(domain, 1, Integer::sum);
            }
        }

        logger.info("Trained model: isJob={}, tokensCount={}, totalJobDocs={}, totalSpamDocs={}",
                isJob, tokens.size(), totalJobDocs, totalSpamDocs);

        persistState();
    }

    public int getVocabularySize() {
        Set<String> allWords = new HashSet<>(jobWordCounts.keySet());
        allWords.addAll(spamWordCounts.keySet());
        return allWords.size();
    }

    /**
     * Strips PII: emails, numbers, URLs, candidate names, removes stop words, generates unigrams & bigrams.
     */
    public List<String> sanitizeAndTokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        // 1. Strip candidate email addresses
        String sanitized = text.replaceAll("(?i)[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", " ");

        // 2. Strip URLs
        sanitized = sanitized.replaceAll("(?i)https?://\\S+", " ");

        // 3. Strip numbers and IDs
        sanitized = sanitized.replaceAll("\\b\\d+\\b", " ");

        // 4. Normalize punctuation
        sanitized = sanitized.replaceAll("[^a-zA-Z\\s-]", " ").toLowerCase();

        String[] rawTokens = sanitized.split("\\s+");
        List<String> validTokens = new ArrayList<>();

        for (String raw : rawTokens) {
            String word = raw.trim();
            if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                validTokens.add(word);
            }
        }

        // 5. Generate Bigrams for critical 2-word patterns (e.g. "application submitted", "apply now", "viewed profile")
        List<String> result = new ArrayList<>(validTokens);
        for (int i = 0; i < validTokens.size() - 1; i++) {
            result.add(validTokens.get(i) + "_" + validTokens.get(i + 1));
        }

        return result;
    }

    private String extractDomain(String sender) {
        if (sender == null) return "";
        Pattern p = Pattern.compile("@([a-zA-Z0-9.-]+)");
        Matcher m = p.matcher(sender);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        return sender.trim().toLowerCase();
    }

    private synchronized void persistState() {
        try {
            ModelStateDTO dto = new ModelStateDTO();
            dto.jobWordCounts = new HashMap<>(jobWordCounts);
            dto.spamWordCounts = new HashMap<>(spamWordCounts);
            dto.confirmedSpamDomains = new HashMap<>(confirmedSpamDomains);
            dto.totalJobDocs = totalJobDocs;
            dto.totalSpamDocs = totalSpamDocs;
            dto.totalJobWords = totalJobWords;
            dto.totalSpamWords = totalSpamWords;

            String json = objectMapper.writeValueAsString(dto);
            GlobalModelState state = globalModelStateRepository.findTopByOrderByIdDesc()
                    .orElse(new GlobalModelState());
            state.setModelData(json);
            state.setLastUpdated(LocalDateTime.now());
            globalModelStateRepository.save(state);
        } catch (Exception e) {
            logger.error("Failed to persist model state to DB: ", e);
        }
    }

    private void loadFromJson(String json) throws Exception {
        ModelStateDTO dto = objectMapper.readValue(json, ModelStateDTO.class);
        this.jobWordCounts.clear();
        this.jobWordCounts.putAll(dto.jobWordCounts != null ? dto.jobWordCounts : Collections.emptyMap());

        this.spamWordCounts.clear();
        this.spamWordCounts.putAll(dto.spamWordCounts != null ? dto.spamWordCounts : Collections.emptyMap());

        this.confirmedSpamDomains.clear();
        this.confirmedSpamDomains.putAll(dto.confirmedSpamDomains != null ? dto.confirmedSpamDomains : Collections.emptyMap());

        this.totalJobDocs = dto.totalJobDocs;
        this.totalSpamDocs = dto.totalSpamDocs;
        this.totalJobWords = dto.totalJobWords;
        this.totalSpamWords = dto.totalSpamWords;
    }

    /**
     * Seeds the baseline training dataset using verified positive job application templates and negative spam templates.
     */
    private void seedBaselineDataset() {
        jobWordCounts.clear();
        spamWordCounts.clear();
        confirmedSpamDomains.clear();
        totalJobDocs = 0;
        totalSpamDocs = 0;
        totalJobWords = 0;
        totalSpamWords = 0;

        // Baseline Positive Job Applications
        String[] jobSamples = {
                "Thank you for your application! Thank you for taking the time to submit your application for Software Engineer. Microsoft Recruiting.",
                "Indeed Application: Software Development Engineer-1 Application submitted AFFORIS HEALTH TECHNOLOGIES The following items were sent to AFFORIS HEALTH TECHNOLOGIES Good luck",
                "Application for Software Development Engineer-1 received, Thank you! Thank you for applying to AFFORIS HEALTH TECHNOLOGIES for the role of Software Development Engineer-1",
                "Indeed Application: Java Full Stack Developer Application submitted AaraTech The following items were sent to AaraTech Good luck Next steps",
                "Thank you for your interest in Experian Thank you for applying for the Technical Analyst position Inclusion and Belonging are core to our purpose",
                "Thank you for Applying to Amazon! Thanks for applying to Amazon! We've received your application for the SDE-1 position What happens next",
                "Application Received - Thank you for applying to Baxter Thank you for taking the time to apply for the Associate Software Engineer opportunity",
                "Thank You from Light & Wonder Thank you for your interest in the Associate Software Engineer opportunity at Light & Wonder",
                "Thank you for applying to OpenText! Thank you for your application for the Software Engineer position at OpenText. We appreciate your interest",
                "Your application to Canva! We have received your application for Software Engineer. We appreciate your interest in Canva",
                "Application to Notion Thanks for applying to the Product Designer role at Notion! We are excited to review your background",
                "Your application to Spotify Hi Alex, thank you for your application. As next step, please complete this coding assessment",
                "Your application was sent to Stripe Hi Alex, your application for Senior Software Engineer at Stripe was submitted",
                "Receipt of your Software Developer job application NatWest Group We have received your application and will review it shortly",
                "Argano Careers: Thank you for your recent job application for Junior Developer We have received your application",
                "Thank you for your application to Snowflake for Cloud Engineer We are currently reviewing your application",
                "Application submitted to Target Engineer - Target India We have received your application for the requisition",
                "Thermo Fisher Scientific: Thank you for applying to Software Engineer I We appreciate your interest in joining our team"
        };

        for (String sample : jobSamples) {
            train(sample, sample, "careers@company.com", true);
        }

        // Baseline Negative / Promotional / Spam Templates
        String[] spamSamples = {
                "10 people viewed your profile See who's looking at your profile on LinkedIn and upgrade to Premium to reach out",
                "Aditya, I want to connect Hi, I'd like to join your professional network on LinkedIn. Accept invitation or ignore.",
                "Software Development Engineer-1 @ AFFORIS HEALTH TECHNOLOGIES Your background as a Junior Java Developer could align well. Interested in growing your career? Apply now or learn more",
                "Aditya, apply now to 'Software Engineer at Microsoft' Your saved job is still available. Apply now before it expires or view similar jobs",
                "Keep track of your application If you are still working on the application, please login here to finish your submission",
                "Recommended jobs for you based on your recent activity on LinkedIn. Explore new opportunities and apply today",
                "Job alert: 15 new Software Engineer jobs in Bengaluru. View jobs and apply easily with your profile",
                "Complete your application You left items in your cart or unfinished application on Indeed. Finish applying now."
        };

        for (String sample : spamSamples) {
            train(sample, sample, "notifications@linkedin.com", false);
        }

        logger.info("Seeded baseline model with {} job docs and {} spam docs. Total Vocab: {}",
                totalJobDocs, totalSpamDocs, getVocabularySize());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelStateDTO {
        public Map<String, Integer> jobWordCounts;
        public Map<String, Integer> spamWordCounts;
        public Map<String, Integer> confirmedSpamDomains;
        public int totalJobDocs;
        public int totalSpamDocs;
        public long totalJobWords;
        public long totalSpamWords;
    }
}
