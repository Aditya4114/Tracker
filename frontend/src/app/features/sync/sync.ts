import { Component, OnInit, ChangeDetectorRef, Output, EventEmitter } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { inject } from '@angular/core';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-sync',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './sync.html',
  styleUrls: ['./sync.css']
})
export class Sync implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  @Output() dateRangeChange = new EventEmitter<{ startDate: string; endDate: string }>();
  @Output() syncCompleted = new EventEmitter<void>();

  startDate: string = '';
  endDate: string = '';
  minDate: string = '';
  maxDate: string = '';

  syncing = false;
  statusMessage = '';
  isError = false;

  ngOnInit() {
    const today = new Date();
    const oneMonthAgo = new Date();
    oneMonthAgo.setDate(today.getDate() - 30);
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(today.getDate() - 7);

    this.maxDate = today.toISOString().split('T')[0];
    this.minDate = oneMonthAgo.toISOString().split('T')[0];
    this.endDate = this.maxDate;
    this.startDate = sevenDaysAgo.toISOString().split('T')[0];

    // Emit initial date range
    this.dateRangeChange.emit({ startDate: this.startDate, endDate: this.endDate });
  }

  onDateChange() {
    if (this.startDate < this.minDate) {
      this.startDate = this.minDate;
    }
    if (this.startDate > this.endDate) {
      this.startDate = this.endDate;
    }
    this.dateRangeChange.emit({ startDate: this.startDate, endDate: this.endDate });
  }

  triggerSync() {
    this.syncing = true;
    this.statusMessage = 'Syncing...';
    this.isError = false;

    const headers = new HttpHeaders().set('Authorization', 'Bearer ' + this.auth.getToken());

    this.http.post<any>('http://localhost:8080/api/sync/start', {
      startDate: this.startDate,
      endDate: this.endDate
    }, { headers }).subscribe({
      next: (res) => {
        this.syncing = false;
        this.isError = false;
        this.statusMessage = res?.message || 'Sync completed.';
        this.syncCompleted.emit();
        this.cdr.detectChanges();
        setTimeout(() => {
          this.statusMessage = '';
          this.cdr.detectChanges();
        }, 4000);
      },
      error: (err) => {
        this.syncing = false;
        this.isError = true;
        this.statusMessage = err?.error?.message || err?.error || 'Sync failed.';
        this.cdr.detectChanges();
      }
    });
  }
}
