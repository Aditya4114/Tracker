import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Sync } from '../sync/sync';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [Sync, CommonModule, FormsModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class Dashboard implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);

  activeView: 'overview' | 'applications' = 'overview';
  activeAppTab: 'unconfirmed' | 'needsEdit' | 'all' = 'unconfirmed';

  metrics = {
    totalApplications: 0,
    inProgress: 0,
    rejected: 0,
    responseRate: 0
  };

  manualReviewList: any[] = [];
  allApplications: any[] = [];
  pendingEmails: any[] = [];

  currentStartDate = '';
  currentEndDate = '';
  isLoading = false;
  isPendingLoading = false;
  isProcessingFeedback: { [key: number]: boolean } = {};
  feedbackMessage = '';

  // Edit Modal State
  showEditModal = false;
  selectedReviewItem: any = null;
  editError = '';
  isSavingEdit = false;

  ngOnInit() {
    this.fetchPendingEmails();
  }

  switchView(view: 'overview' | 'applications') {
    this.activeView = view;
    if (view === 'applications') {
      this.fetchPendingEmails();
      if (!this.allApplications.length) {
        this.fetchDashboardData();
      }
    }
  }

  switchAppTab(tab: 'unconfirmed' | 'needsEdit' | 'all') {
    this.activeAppTab = tab;
    if (tab === 'unconfirmed') {
      this.fetchPendingEmails();
    }
  }

  onDateRangeChange(range: { startDate: string; endDate: string }) {
    this.currentStartDate = range.startDate;
    this.currentEndDate = range.endDate;
    this.fetchDashboardData();
    this.fetchPendingEmails();
  }

  onSyncCompleted() {
    this.fetchDashboardData();
    this.fetchPendingEmails();
  }

  fetchDashboardData() {
    if (!this.currentStartDate || !this.currentEndDate) return;

    this.isLoading = true;
    const headers = new HttpHeaders().set('Authorization', 'Bearer ' + this.auth.getToken());

    const url = `http://localhost:8080/api/dashboard?startDate=${this.currentStartDate}&endDate=${this.currentEndDate}`;
    this.http.get<any>(url, { headers }).subscribe({
      next: (data) => {
        this.isLoading = false;
        this.metrics = {
          totalApplications: data.totalApplications || 0,
          inProgress: data.inProgress || 0,
          rejected: data.rejected || 0,
          responseRate: data.responseRate || 0
        };
        this.manualReviewList = data.manualReviewList || [];
        this.allApplications = data.applications || [];
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        console.error('Failed to load dashboard data:', err);
        this.cdr.detectChanges();
      }
    });
  }

  fetchPendingEmails() {
    const token = this.auth.getToken();
    if (!token) return;

    this.isPendingLoading = true;
    const headers = new HttpHeaders().set('Authorization', 'Bearer ' + token);

    this.http.get<any[]>('http://localhost:8080/api/emails/pending', { headers }).subscribe({
      next: (emails) => {
        this.isPendingLoading = false;
        this.pendingEmails = (emails || []).map(e => ({
          ...e,
          gmailLink: e.messageId ? `https://mail.google.com/mail/u/0/#all/${e.messageId}` : null
        }));
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isPendingLoading = false;
        console.error('Failed to fetch pending emails:', err);
        this.cdr.detectChanges();
      }
    });
  }

  submitEmailFeedback(emailId: number, isJob: boolean) {
    this.isProcessingFeedback[emailId] = true;
    this.feedbackMessage = '';
    const headers = new HttpHeaders().set('Authorization', 'Bearer ' + this.auth.getToken());

    const url = `http://localhost:8080/api/emails/pending/${emailId}/feedback?isJob=${isJob}`;
    this.http.post<any>(url, {}, { headers }).subscribe({
      next: (res) => {
        delete this.isProcessingFeedback[emailId];
        this.pendingEmails = this.pendingEmails.filter(e => e.id !== emailId);
        this.feedbackMessage = res?.message || (isJob ? 'Email marked as job application.' : 'Email dismissed as not a job.');
        setTimeout(() => {
          this.feedbackMessage = '';
          this.cdr.detectChanges();
        }, 4000);

        if (isJob) {
          this.fetchDashboardData();
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        delete this.isProcessingFeedback[emailId];
        this.feedbackMessage = 'Failed to submit feedback. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  openEditModal(item: any) {
    this.selectedReviewItem = {
      ...item,
      company: this.isMissing(item.company) ? '' : item.company,
      position: this.isMissing(item.position) ? '' : item.position
    };
    this.editError = '';
    this.showEditModal = true;
  }

  closeEditModal() {
    this.showEditModal = false;
    this.selectedReviewItem = null;
    this.editError = '';
  }

  saveEdit() {
    if (!this.selectedReviewItem) return;

    if (!this.selectedReviewItem.company || !this.selectedReviewItem.company.trim()) {
      this.editError = 'Company name is required.';
      return;
    }
    if (!this.selectedReviewItem.position || !this.selectedReviewItem.position.trim()) {
      this.editError = 'Position / Role is required.';
      return;
    }

    this.isSavingEdit = true;
    this.editError = '';

    const headers = new HttpHeaders().set('Authorization', 'Bearer ' + this.auth.getToken());
    const payload = {
      company: this.selectedReviewItem.company.trim(),
      position: this.selectedReviewItem.position.trim(),
      status: this.selectedReviewItem.currentStatus
    };

    this.http.put<any>(`http://localhost:8080/api/applications/${this.selectedReviewItem.id}`, payload, { headers }).subscribe({
      next: () => {
        this.isSavingEdit = false;
        this.closeEditModal();
        this.fetchDashboardData();
      },
      error: (err) => {
        this.isSavingEdit = false;
        this.editError = err?.error?.message || 'Failed to update application.';
        this.cdr.detectChanges();
      }
    });
  }

  isDeletingSpam: { [key: number]: boolean } = {};

  markApplicationAsSpam(item: any) {
    if (!item || !item.id) return;

    this.isDeletingSpam[item.id] = true;
    const headers = new HttpHeaders().set('Authorization', 'Bearer ' + this.auth.getToken());

    this.http.post<any>(`http://localhost:8080/api/applications/${item.id}/mark-as-spam`, {}, { headers }).subscribe({
      next: (res) => {
        delete this.isDeletingSpam[item.id];
        this.manualReviewList = this.manualReviewList.filter(a => a.id !== item.id);
        this.allApplications = this.allApplications.filter(a => a.id !== item.id);
        if (this.metrics.totalApplications > 0) {
          this.metrics.totalApplications--;
        }
        if (this.showEditModal && this.selectedReviewItem?.id === item.id) {
          this.closeEditModal();
        }
        this.feedbackMessage = res?.message || 'Application marked as spam and removed.';
        setTimeout(() => {
          this.feedbackMessage = '';
          this.cdr.detectChanges();
        }, 4000);
        this.cdr.detectChanges();
      },
      error: (err) => {
        delete this.isDeletingSpam[item.id];
        this.feedbackMessage = 'Failed to mark as spam. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  isMissing(val: string | null | undefined): boolean {
    return !val || val.trim() === '' || val.trim().toLowerCase() === 'not available';
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
