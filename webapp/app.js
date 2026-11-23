/**
 * elearnyar - iOS Web App (PWA) Client Logic
 * Version 1.7 Feature Parity
 */

// ==========================================
// 1. Jalali (Persian) Date Converter
// ==========================================
function gregorianToJalali(gy, gm, gd) {
  const g_d_m = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
  let jy = (gy <= 1600) ? 0 : 979;
  gy -= (gy <= 1600) ? 621 : 1600;
  const gy2 = (gm > 2) ? (gy + 1) : gy;
  let days = (365 * gy) + Math.floor((gy2 + 3) / 4) - Math.floor((gy2 + 99) / 100)
    + Math.floor((gy2 + 399) / 400) - 80 + gd + g_d_m[gm - 1];
  jy += 33 * Math.floor(days / 12053);
  days %= 12053;
  jy += 4 * Math.floor(days / 1461);
  days %= 1461;
  jy += Math.floor((days - 1) / 365);
  if (days > 0) days = (days - 1) % 365;
  const jm = (days < 186) ? 1 + Math.floor(days / 31) : 7 + Math.floor((days - 186) / 30);
  const jd = 1 + ((days < 186) ? (days % 31) : ((days - 186) % 30));
  return [jy, jm, jd];
}

function toPersianDigits(str) {
  if (str === null || str === undefined) return '';
  const persianDigits = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];
  return String(str).replace(/[0-9]/g, (w) => persianDigits[+w]);
}

function formatPersianDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return toPersianDigits(dateStr);
  const [jy, jm, jd] = gregorianToJalali(d.getFullYear(), d.getMonth() + 1, d.getDate());
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  return toPersianDigits(`${jy}/${String(jm).padStart(2, '0')}/${String(jd).padStart(2, '0')} ساعت ${hours}:${minutes}`);
}

function calculateCountdown(endDateStr) {
  const deadline = new Date(endDateStr);
  if (isNaN(deadline.getTime())) return { text: 'موعد نامشخص', isExpired: false, class: 'countdown-normal' };
  const now = new Date();
  const diffMs = deadline - now;
  if (diffMs <= 0) return { text: 'مهلت پایان یافته', isExpired: true, class: 'countdown-normal' };

  const days = Math.floor(diffMs / (24 * 3600 * 1000));
  const hours = Math.floor((diffMs % (24 * 3600 * 1000)) / (3600 * 1000));
  const minutes = Math.floor((diffMs % (3600 * 1000)) / (60 * 1000));

  let text = '';
  let cls = 'countdown-normal';

  if (days > 0) {
    text = `⏳ ${days} روز و ${hours} ساعت مانده`;
    cls = (days <= 1) ? 'countdown-warning' : 'countdown-normal';
  } else if (hours > 0) {
    text = `⚠️ ${hours} ساعت و ${minutes} دقیقه مانده`;
    cls = (hours < 3) ? 'countdown-urgent' : 'countdown-warning';
  } else {
    text = `🚨 ${minutes} دقیقه تا پایان مهلت!`;
    cls = 'countdown-urgent';
  }
  return { text: toPersianDigits(text), isExpired: false, class: cls };
}

// ==========================================
// 2. Application State & Storage
// ==========================================
const State = {
  isLoggedIn: false,
  username: '',
  password: '',
  proxyUrl: localStorage.getItem('elearn_proxy_url') || '',
  courses: [],
  activeExercises: [],
  expiredExercises: [],
  messages: [],
  unseenNewIds: new Set(JSON.parse(localStorage.getItem('elearn_unseen_ids') || '[]')),
  knownExerciseIds: new Set(JSON.parse(localStorage.getItem('elearn_known_ids') || '[]')),
  expandedCourses: new Set(),
  expandedAttendance: new Set(),
  isExpiredExpanded: false
};

// ==========================================
// 3. UI Elements Selection
// ==========================================
const UI = {
  btnSync: document.getElementById('btn-sync'),
  btnSettings: document.getElementById('btn-settings'),
  tvLastSync: document.getElementById('tv-last-sync'),
  navTabs: document.querySelectorAll('.nav-tab'),
  tabViews: document.querySelectorAll('.tab-view'),
  layoutSyncProgress: document.getElementById('layout-sync-progress'),
  tvSyncStatus: document.getElementById('tv-sync-status'),
  layoutNewBanner: document.getElementById('layout-new-banner'),
  tvNewBannerText: document.getElementById('tv-new-banner-text'),
  btnDismissBanner: document.getElementById('btn-dismiss-banner'),
  tvActiveCountBadge: document.getElementById('tv-active-count-badge'),
  listActiveExercises: document.getElementById('list-active-exercises'),
  layoutEmptyActive: document.getElementById('layout-empty-active'),
  headerExpired: document.getElementById('header-expired'),
  ivExpiredArrow: document.getElementById('iv-expired-arrow'),
  tvExpiredCountBadge: document.getElementById('tv-expired-count-badge'),
  listExpiredExercises: document.getElementById('list-expired-exercises'),
  listCoursesAccordion: document.getElementById('list-courses-accordion'),
  listMessages: document.getElementById('list-messages'),
  layoutEmptyMessages: document.getElementById('layout-empty-messages'),
  listAttendance: document.getElementById('list-attendance'),
  layoutEmptyAttendance: document.getElementById('layout-empty-attendance'),
  modalLogin: document.getElementById('modal-login'),
  formLogin: document.getElementById('form-login'),
  inputUsername: document.getElementById('input-username'),
  inputPassword: document.getElementById('input-password'),
  btnUseDemo: document.getElementById('btn-use-demo'),
  modalSettings: document.getElementById('modal-settings'),
  btnCloseSettings: document.getElementById('btn-close-settings'),
  tvSettingsUser: document.getElementById('tv-settings-user'),
  inputProxyUrl: document.getElementById('input-proxy-url'),
  btnSaveSettings: document.getElementById('btn-save-settings'),
  btnLogout: document.getElementById('btn-logout')
};

// ==========================================
// 4. Tab Navigation Logic
// ==========================================
UI.navTabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    UI.navTabs.forEach((t) => t.classList.remove('active'));
    tab.classList.add('active');
    const tabName = tab.getAttribute('data-tab');

    UI.tabViews.forEach((view) => {
      if (view.id === `view-${tabName}`) {
        view.style.display = 'block';
      } else {
        view.style.display = 'none';
      }
    });
  });
});

// Expired Section Accordion
UI.headerExpired.addEventListener('click', () => {
  State.isExpiredExpanded = !State.isExpiredExpanded;
  UI.listExpiredExercises.style.display = State.isExpiredExpanded ? 'flex' : 'none';
  UI.ivExpiredArrow.classList.toggle('expanded', State.isExpiredExpanded);
});

// Dismiss New Exercises Banner
UI.btnDismissBanner.addEventListener('click', () => {
  State.unseenNewIds.clear();
  localStorage.setItem('elearn_unseen_ids', JSON.stringify([]));
  UI.layoutNewBanner.style.display = 'none';
  renderActiveExercises();
});

// ==========================================
// 5. Render Methods
// ==========================================
function renderActiveExercises() {
  UI.listActiveExercises.innerHTML = '';
  const count = State.activeExercises.length;
  UI.tvActiveCountBadge.textContent = toPersianDigits(`${count} تمرین فعال`);
  UI.layoutEmptyActive.style.display = (count === 0) ? 'flex' : 'none';

  State.activeExercises.forEach((ex) => {
    const cd = calculateCountdown(ex.endDate);
    const isNew = State.unseenNewIds.has(ex.exid);

    const card = document.createElement('div');
    card.className = 'exercise-card glass-card';
    card.innerHTML = `
      <div class="card-top-row">
        <div class="course-and-badge">
          <span class="badge-course">${ex.courseName}</span>
          ${isNew ? '<span class="badge-new">• جدید</span>' : ''}
        </div>
        <span class="badge-countdown ${cd.class}">${cd.text}</span>
      </div>
      <h3 class="card-title">${ex.title}</h3>
      <div class="card-info-row">
        <span>مهلت: ${formatPersianDate(ex.endDate)}</span>
        <span>${ex.grade ? `نمره: ${toPersianDigits(ex.grade)}` : ''}</span>
      </div>
      ${ex.description ? `<p style="font-size: 12px; color: var(--text-secondary);">${ex.description}</p>` : ''}
      <div class="card-divider"></div>
      <div class="card-actions-row">
        <button class="btn-card-action" onclick="handleDownload('${ex.exGuid}', '${ex.filePath || ex.title}')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
            <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/>
          </svg>
          دانلود تمرین
        </button>
      </div>
    `;
    UI.listActiveExercises.appendChild(card);
  });
}

function renderExpiredExercises() {
  UI.listExpiredExercises.innerHTML = '';
  const count = State.expiredExercises.length;
  UI.tvExpiredCountBadge.textContent = toPersianDigits(`${count} منسوخ`);

  State.expiredExercises.forEach((ex) => {
    const card = document.createElement('div');
    card.className = 'exercise-card glass-card';
    card.style.opacity = '0.75';
    card.innerHTML = `
      <div class="card-top-row">
        <span class="badge-course">${ex.courseName}</span>
        <span class="badge-gray">پایان‌یافته</span>
      </div>
      <h4 class="card-title">${ex.title}</h4>
      <div class="card-info-row">
        <span>پایان: ${formatPersianDate(ex.endDate)}</span>
      </div>
    `;
    UI.listExpiredExercises.appendChild(card);
  });
}

function renderLearningAccordion() {
  UI.listCoursesAccordion.innerHTML = '';
  State.courses.forEach((c) => {
    const isExpanded = State.expandedCourses.has(c.tcInfoId);
    const files = c.learningFiles || [];

    const card = document.createElement('div');
    card.className = 'glass-card';
    card.innerHTML = `
      <div class="accordion-header" onclick="toggleCourse('${c.tcInfoId}')">
        <svg class="chevron-icon ${isExpanded ? 'expanded' : ''}" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
          <path d="M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z"/>
        </svg>
        <div class="accordion-title-col">
          <h3 class="accordion-title">${c.name}</h3>
          <p class="accordion-subtitle">استاد: ${c.teacherName} (گروه ${toPersianDigits(c.groupNo)})</p>
        </div>
        <span class="badge-emerald">${toPersianDigits(files.length)} فایل</span>
      </div>
      <div class="accordion-content" style="display: ${isExpanded ? 'flex' : 'none'};">
        ${files.length === 0 ? '<p style="text-align:center; padding:12px; color:var(--text-secondary); font-size:12px;">هیچ فایل آموزشی برای این درس ثبت نشده است.</p>' : ''}
        ${files.map((f) => `
          <div class="file-item-row">
            <div class="file-info-col">
              <span class="file-name">${f.title}</span>
              <span class="file-date">${formatPersianDate(f.insertDate)}</span>
            </div>
            <button class="btn-card-action" onclick="handleDownload('${f.fGuid || f.leGuid}', '${f.filePath || f.title}')">
              دانلود
            </button>
          </div>
        `).join('')}
      </div>
    `;
    UI.listCoursesAccordion.appendChild(card);
  });
}

function renderMessages() {
  UI.listMessages.innerHTML = '';
  const count = State.messages.length;
  UI.layoutEmptyMessages.style.display = (count === 0) ? 'flex' : 'none';

  State.messages.forEach((m) => {
    const card = document.createElement('div');
    card.className = 'exercise-card glass-card';
    card.innerHTML = `
      <div class="card-top-row">
        <div class="course-and-badge">
          <span class="badge-course">${m.courseName}</span>
          ${!m.isRead ? '<span class="badge-new">• جدید</span>' : ''}
        </div>
        <span style="font-size: 11px; color: var(--text-secondary);">${formatPersianDate(m.insertDate)}</span>
      </div>
      <h3 class="card-title">${m.title}</h3>
      <div class="card-info-row">
        <span>فرستنده: <strong style="color:var(--text-primary);">${m.senderName}</strong></span>
      </div>
      <div class="card-divider"></div>
      <p style="font-size: 13px; line-height: 1.6; color: #E2E8F0; white-space: pre-wrap;">${m.message}</p>
    `;
    UI.listMessages.appendChild(card);
  });
}

function renderAttendance() {
  UI.listAttendance.innerHTML = '';
  UI.layoutEmptyAttendance.style.display = (State.courses.length === 0) ? 'flex' : 'none';

  State.courses.forEach((c) => {
    const isExpanded = State.expandedAttendance.has(c.tcInfoId);
    const sessions = c.attendanceList || [];
    const presentCount = sessions.filter((s) => s.status).length;
    const absentCount = sessions.filter((s) => !s.status).length;
    const summary = toPersianDigits(`${presentCount} حاضر / ${absentCount} غایب`);

    const card = document.createElement('div');
    card.className = 'glass-card';
    card.innerHTML = `
      <div class="accordion-header" onclick="toggleAttendance('${c.tcInfoId}')">
        <svg class="chevron-icon ${isExpanded ? 'expanded' : ''}" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
          <path d="M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z"/>
        </svg>
        <div class="accordion-title-col">
          <h3 class="accordion-title">${c.name}</h3>
          <p class="accordion-subtitle">استاد: ${c.teacherName}</p>
        </div>
        <span class="badge-course">${summary}</span>
      </div>
      <div class="accordion-content" style="display: ${isExpanded ? 'flex' : 'none'};">
        ${sessions.length === 0 ? '<p style="text-align:center; padding:12px; color:var(--text-secondary); font-size:12px;">هنوز اطلاعات حضور و غیابی برای این درس ثبت نشده است.</p>' : ''}
        ${sessions.map((s) => `
          <div class="session-row">
            <div class="session-info">
              <span class="session-day-time">${s.dayName} ${s.startTime ? ` - ساعت ${toPersianDigits(s.startTime)}` : ''}</span>
              <span class="session-date">${formatPersianDate(s.insertDate)}</span>
            </div>
            <span class="${s.status ? 'badge-present' : 'badge-absent'}">
              ${s.status ? '✔️ حاضر' : '❌ غایب'}
            </span>
          </div>
        `).join('')}
      </div>
    `;
    UI.listAttendance.appendChild(card);
  });
}

function toggleCourse(tcInfoId) {
  if (State.expandedCourses.has(tcInfoId)) {
    State.expandedCourses.delete(tcInfoId);
  } else {
    State.expandedCourses.add(tcInfoId);
  }
  renderLearningAccordion();
}

function toggleAttendance(tcInfoId) {
  if (State.expandedAttendance.has(tcInfoId)) {
    State.expandedAttendance.delete(tcInfoId);
  } else {
    State.expandedAttendance.add(tcInfoId);
  }
  renderAttendance();
}

function updateAllUI() {
  renderActiveExercises();
  renderExpiredExercises();
  renderLearningAccordion();
  renderMessages();
  renderAttendance();

  // Banner for new exercises
  if (State.unseenNewIds.size > 0) {
    const n = State.unseenNewIds.size;
    UI.tvNewBannerText.textContent = (n === 1) ?
      '🔔 یک تمرین جدید در سامانه ثبت شده است!' :
      `🔔 ${toPersianDigits(n)} تمرین جدید در سامانه ثبت شده است!`;
    UI.layoutNewBanner.style.display = 'flex';
  } else {
    UI.layoutNewBanner.style.display = 'none';
  }
}

// ==========================================
// 6. Data Parsing & Caching
// ==========================================
function parseCoursesData(courses) {
  State.courses = courses;
  State.activeExercises = [];
  State.expiredExercises = [];
  State.messages = [];

  const newlyAdded = new Set();
  const currentIds = new Set();
  const isFirst = (State.knownExerciseIds.size === 0);

  courses.forEach((c) => {
    if (c.exercises) {
      c.exercises.forEach((ex) => {
        ex.courseName = c.name;
        currentIds.add(ex.exid);
        const cd = calculateCountdown(ex.endDate);
        if (cd.isExpired) {
          State.expiredExercises.push(ex);
        } else {
          State.activeExercises.push(ex);
          if (!isFirst && !State.knownExerciseIds.has(ex.exid)) {
            newlyAdded.add(ex.exid);
            State.unseenNewIds.add(ex.exid);
          }
        }
      });
    }

    if (c.messages) {
      c.messages.forEach((m) => {
        m.courseName = c.name;
        State.messages.push(m);
      });
    }
  });

  State.knownExerciseIds = currentIds;
  localStorage.setItem('elearn_known_ids', JSON.stringify(Array.from(currentIds)));
  localStorage.setItem('elearn_unseen_ids', JSON.stringify(Array.from(State.unseenNewIds)));
  localStorage.setItem('elearn_cached_courses', JSON.stringify(courses));

  const now = new Date();
  const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  UI.tvLastSync.textContent = toPersianDigits(`آخرین بروزرسانی: امروز ساعت ${timeStr}`);
  localStorage.setItem('elearn_last_sync', UI.tvLastSync.textContent);

  updateAllUI();
}

// ==========================================
// 7. API Client & Synchronization
// ==========================================
async function performSync() {
  if (!State.isLoggedIn) return;
  UI.btnSync.classList.add('spinning');
  UI.layoutSyncProgress.style.display = 'flex';
  UI.tvSyncStatus.textContent = 'در حال اتصال به سامانه ایلرن!...';

  try {
    const proxy = State.proxyUrl || '';
    const endpoint = proxy ? `${proxy}/api/courses` : '/api/courses';

    const resp = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: State.username, password: State.password })
    });

    const data = await resp.json().catch(() => ({}));

    if (!resp.ok) {
      let errMsg = data.error || `خطای سرور (${resp.status})`;
      if (resp.status === 401 || errMsg === '10') {
        errMsg = 'نام کاربری یا کلمه عبور اشتباه است.';
      }
      throw new Error(errMsg);
    }

    if (data.courses) {
      parseCoursesData(data.courses);
    } else {
      throw new Error('اطلاعاتی از سامانه دریافت نشد.');
    }
  } catch (err) {
    console.warn('Sync error:', err.message);
    UI.tvSyncStatus.textContent = err.message || 'خطا در برقراری ارتباط با سامانه ایلرن!';
    setTimeout(() => {
      UI.layoutSyncProgress.style.display = 'none';
    }, 3500);
  } finally {
    UI.btnSync.classList.remove('spinning');
    setTimeout(() => {
      UI.layoutSyncProgress.style.display = 'none';
    }, 1500);
  }
}

function handleDownload(guid, filename) {
  const proxy = State.proxyUrl || '';
  const downloadUrl = proxy ? `${proxy}/api/download?guid=${encodeURIComponent(guid)}` : `https://elearn.uk.ac.ir/Downloader.aspx?guid=${encodeURIComponent(guid)}`;
  window.open(downloadUrl, '_blank');
}

// ==========================================
// 8. Demo Mode (Sample Data)
// ==========================================
function loadDemoData() {
  const now = new Date();
  const d3 = new Date(now.getTime() + 3 * 24 * 3600 * 1000).toISOString();
  const d1 = new Date(now.getTime() + 14 * 3600 * 1000).toISOString();
  const dPast = new Date(now.getTime() - 2 * 24 * 3600 * 1000).toISOString();

  const demoCourses = [
    {
      tcInfoId: "101",
      name: "محاسبات عددی در اپتیک",
      teacherName: "منصوره امان الهي",
      groupNo: "01",
      exercises: [
        { exid: "ex_1", title: "تکلیف سری چهارم: روش‌های نیوتن-رافسون", endDate: d3, grade: 20, exGuid: "demo1" }
      ],
      learningFiles: [
        { lid: "f_1", title: "اسلایدهای جلسه ۶: درون‌یابی لاگرانژ.pdf", insertDate: "2026-11-10T10:00:00" },
        { lid: "f_2", title: "کدهای متلب روش تنصیف.zip", insertDate: "2026-11-05T14:30:00" }
      ],
      messages: [
        { id: "m_1", title: "کلاس جبرانی روز سه‌شنبه", senderName: "منصوره امان الهي", insertDate: "2026-11-16T12:00:00", message: "دانشجویان گرامی، کلاس جبرانی محاسبات عددی روز سه‌شنبه ساعت ۱۰ در سامانه برگزار خواهد شد.", isRead: false }
      ],
      attendanceList: [
        { dayName: "دوشنبه", startTime: "10:00", insertDate: "2026-11-10", status: true },
        { dayName: "چهارشنبه", startTime: "10:00", insertDate: "2026-11-12", status: true }
      ]
    },
    {
      tcInfoId: "102",
      name: "آزمایشگاه الکترونیک ۲",
      teacherName: "بیژن کی نیا",
      groupNo: "02",
      exercises: [
        { exid: "ex_2", title: "گزارش‌کار آزمایش تقویت‌کننده تفاضلی", endDate: d1, grade: 18.5, exGuid: "demo2" }
      ],
      learningFiles: [
        { lid: "f_3", title: "دستورکار آزمایش چهارم.pdf", insertDate: "2026-11-08T09:00:00" }
      ],
      messages: [],
      attendanceList: [
        { dayName: "سه‌شنبه", startTime: "14:00", insertDate: "2026-11-11", status: true },
        { dayName: "سه‌شنبه", startTime: "14:00", insertDate: "2026-11-04", status: false }
      ]
    },
    {
      tcInfoId: "103",
      name: "منابع نور و آشکارسازها",
      teacherName: "حميدرضا مشايخي",
      groupNo: "01",
      exercises: [
        { exid: "ex_3", title: "تمرین کلاسی فصل فوتودیودها", endDate: dPast, grade: 20, exGuid: "demo3" }
      ],
      learningFiles: [
        { lid: "f_4", title: "جزوه کامل آشکارسازهای نوری.pdf", insertDate: "2026-11-01T11:00:00" }
      ],
      messages: [
        { id: "m_2", title: "منبع آزمون میان‌ترم", senderName: "حميدرضا مشايخي", insertDate: "2026-11-14T18:20:00", message: "امتحان میان‌ترم از ابتدای فصل ۱ تا پایان بخش ۳ خواهد بود. موفق باشید.", isRead: true }
      ],
      attendanceList: [
        { dayName: "یکشنبه", startTime: "08:00", insertDate: "2026-11-09", status: true }
      ]
    }
  ];

  State.isLoggedIn = true;
  State.username = "کاربر آزمایشی";
  UI.modalLogin.style.display = 'none';
  parseCoursesData(demoCourses);
}

// ==========================================
// 9. Login & Settings Handlers
// ==========================================
UI.btnUseDemo.addEventListener('click', () => {
  loadDemoData();
});

UI.formLogin.addEventListener('submit', async (e) => {
  e.preventDefault();
  const u = UI.inputUsername.value.trim();
  const p = UI.inputPassword.value.trim();
  if (!u || !p) return;

  State.username = u;
  State.password = p;
  State.isLoggedIn = true;
  localStorage.setItem('elearn_user', u);
  localStorage.setItem('elearn_pass', p);
  localStorage.setItem('elearn_logged_in', 'true');
  UI.modalLogin.style.display = 'none';
  UI.tvSettingsUser.textContent = u;

  performSync();
});

UI.btnSync.addEventListener('click', performSync);

UI.btnSettings.addEventListener('click', () => {
  UI.tvSettingsUser.textContent = State.username || 'وارد نشده';
  UI.inputProxyUrl.value = State.proxyUrl || '';
  UI.modalSettings.style.display = 'flex';
});

UI.btnCloseSettings.addEventListener('click', () => {
  UI.modalSettings.style.display = 'none';
});

UI.btnSaveSettings.addEventListener('click', () => {
  State.proxyUrl = UI.inputProxyUrl.value.trim();
  localStorage.setItem('elearn_proxy_url', State.proxyUrl);
  UI.modalSettings.style.display = 'none';
  performSync();
});

UI.btnLogout.addEventListener('click', () => {
  localStorage.removeItem('elearn_user');
  localStorage.removeItem('elearn_pass');
  localStorage.removeItem('elearn_logged_in');
  localStorage.removeItem('elearn_cached_courses');
  State.isLoggedIn = false;
  State.courses = [];
  State.activeExercises = [];
  State.expiredExercises = [];
  State.messages = [];
  UI.modalSettings.style.display = 'none';
  UI.modalLogin.style.display = 'flex';
  updateAllUI();
});

// ==========================================
// 10. Initialization
// ==========================================
window.addEventListener('DOMContentLoaded', () => {
  // Service Worker Registration for PWA on iOS/Desktop
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch((err) => console.log('SW registration failed:', err));
  }

  const savedUser = localStorage.getItem('elearn_user');
  const savedPass = localStorage.getItem('elearn_pass');
  const savedLoggedIn = localStorage.getItem('elearn_logged_in') === 'true';
  const cachedCourses = localStorage.getItem('elearn_cached_courses');
  const lastSync = localStorage.getItem('elearn_last_sync');

  if (lastSync) UI.tvLastSync.textContent = lastSync;

  if (savedLoggedIn && savedUser && savedPass) {
    State.isLoggedIn = true;
    State.username = savedUser;
    State.password = savedPass;
    UI.modalLogin.style.display = 'none';

    if (cachedCourses) {
      try {
        parseCoursesData(JSON.parse(cachedCourses));
      } catch (e) {
        console.error(e);
      }
    } else {
      performSync();
    }
  } else {
    UI.modalLogin.style.display = 'flex';
  }
});
