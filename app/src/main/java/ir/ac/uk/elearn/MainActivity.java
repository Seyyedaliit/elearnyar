package ir.ac.uk.elearn;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements
        ActiveExerciseAdapter.OnExerciseActionListener,
        ExpiredExerciseAdapter.OnDownloadListener,
        CourseAccordionAdapter.OnFileDownloadListener {

    private AppPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI elements
    private TextView tvLastSync;
    private ImageButton btnSync, btnSettings;
    private TabLayout tabLayout;
    private View viewExercises;
    private RecyclerView rvCoursesAccordion;
    private LinearLayout layoutSyncProgress;
    private TextView tvSyncStatus;

    // Login Overlay
    private LinearLayout layoutLogin;
    private TextInputEditText etUsername, etPassword;
    private MaterialButton btnDoLogin;
    private ProgressBar pbLogin;

    // Exercises Tab
    private TextView tvActiveCountBadge;
    private RecyclerView rvActiveExercises;
    private LinearLayout layoutEmptyActive;
    private ActiveExerciseAdapter activeExerciseAdapter;

    // Expired Exercises (Collapsible)
    private View headerExpired;
    private ImageView ivExpiredArrow;
    private TextView tvExpiredCountBadge;
    private RecyclerView rvExpiredExercises;
    private ExpiredExerciseAdapter expiredExerciseAdapter;
    private boolean isExpiredExpanded = false;

    // Learning Tab
    private CourseAccordionAdapter courseAccordionAdapter;

    // Data in-memory
    private final List<Course> courseList = new ArrayList<>();
    private final List<ExerciseItem> activeExercises = new ArrayList<>();
    private final List<ExerciseItem> expiredExercises = new ArrayList<>();

    // File upload picker state
    private ExerciseItem currentUploadExercise;
    private Uri selectedUploadUri;
    private String selectedUploadFilename;
    private TextView tvUploadSelectedFileName;
    private TextView tvUploadSelectedFileSize;
    private MaterialButton btnSubmitUpload;
    private ProgressBar pbUploading;

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedUploadUri = uri;
                    handleSelectedFile(uri);
                }
            }
    );

    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {}
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = AppPreferences.getInstance(this);
            setContentView(R.layout.activity_main);

            initViews();
            setupAdapters();
            setupListeners();
            requestNecessaryPermissions();

            if (prefs.isLoggedIn()) {
                layoutLogin.setVisibility(View.GONE);
                loadDataFromCache();
                performSync(false);
            } else {
                layoutLogin.setVisibility(View.VISIBLE);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void initViews() {
        tvLastSync = findViewById(R.id.tv_last_sync);
        btnSync = findViewById(R.id.btn_sync);
        btnSettings = findViewById(R.id.btn_settings);
        tabLayout = findViewById(R.id.tab_layout);
        viewExercises = findViewById(R.id.view_exercises);
        rvCoursesAccordion = findViewById(R.id.rv_courses_accordion);
        layoutSyncProgress = findViewById(R.id.layout_sync_progress);
        tvSyncStatus = findViewById(R.id.tv_sync_status);

        layoutLogin = findViewById(R.id.layout_login);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnDoLogin = findViewById(R.id.btn_do_login);
        pbLogin = findViewById(R.id.pb_login);

        tvActiveCountBadge = findViewById(R.id.tv_active_count_badge);
        rvActiveExercises = findViewById(R.id.rv_active_exercises);
        layoutEmptyActive = findViewById(R.id.layout_empty_active);

        headerExpired = findViewById(R.id.header_expired);
        ivExpiredArrow = findViewById(R.id.iv_expired_arrow);
        tvExpiredCountBadge = findViewById(R.id.tv_expired_count_badge);
        rvExpiredExercises = findViewById(R.id.rv_expired_exercises);
    }

    private void setupAdapters() {
        activeExerciseAdapter = new ActiveExerciseAdapter(this);
        rvActiveExercises.setLayoutManager(new LinearLayoutManager(this));
        rvActiveExercises.setAdapter(activeExerciseAdapter);

        expiredExerciseAdapter = new ExpiredExerciseAdapter(this);
        rvExpiredExercises.setLayoutManager(new LinearLayoutManager(this));
        rvExpiredExercises.setAdapter(expiredExerciseAdapter);

        courseAccordionAdapter = new CourseAccordionAdapter(this);
        rvCoursesAccordion.setLayoutManager(new LinearLayoutManager(this));
        rvCoursesAccordion.setAdapter(courseAccordionAdapter);
    }

    private void setupListeners() {
        // Tab switching
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    viewExercises.setVisibility(View.VISIBLE);
                    rvCoursesAccordion.setVisibility(View.GONE);
                } else {
                    viewExercises.setVisibility(View.GONE);
                    rvCoursesAccordion.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Expired exercises collapsible section
        if (headerExpired != null) {
            headerExpired.setOnClickListener(v -> {
                isExpiredExpanded = !isExpiredExpanded;
                rvExpiredExercises.setVisibility(isExpiredExpanded ? View.VISIBLE : View.GONE);
                if (ivExpiredArrow != null) {
                    ivExpiredArrow.setImageResource(isExpiredExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
                }
            });
        }

        // Sync button
        btnSync.setOnClickListener(v -> performSync(true));

        // Settings button
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Login button
        btnDoLogin.setOnClickListener(v -> {
            String u = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
            String p = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            if (u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "لطفاً شماره دانشجویی و رمز عبور را وارد کنید.", Toast.LENGTH_SHORT).show();
                return;
            }
            performLogin(u, p);
        });
    }

    private void requestNecessaryPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void performLogin(String username, String password) {
        pbLogin.setVisibility(View.VISIBLE);
        btnDoLogin.setEnabled(false);

        new Thread(() -> {
            try {
                boolean ok = ElearnClient.getInstance().login(username, password);
                mainHandler.post(() -> {
                    pbLogin.setVisibility(View.GONE);
                    btnDoLogin.setEnabled(true);
                    if (ok) {
                        prefs.setUsername(username);
                        prefs.setPassword(password);
                        prefs.setLoggedIn(true);
                        layoutLogin.setVisibility(View.GONE);
                        Toast.makeText(this, "ورود به سامانه با موفقیت انجام شد.", Toast.LENGTH_SHORT).show();
                        performSync(false);
                    } else {
                        Toast.makeText(this, "ورود ناموفق بود.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    pbLogin.setVisibility(View.GONE);
                    btnDoLogin.setEnabled(true);
                    Toast.makeText(this, "خطا در ورود: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void performSync(boolean userTriggered) {
        if (!prefs.isLoggedIn()) return;

        layoutSyncProgress.setVisibility(View.VISIBLE);
        tvSyncStatus.setText("در حال اتصال به سامانه elearn...");

        new Thread(() -> {
            try {
                String u = prefs.getUsername();
                String p = prefs.getPassword();

                List<Course> result = ElearnClient.getInstance().fetchAllData(u, p, (message, current, total) -> {
                    mainHandler.post(() -> {
                        if (tvSyncStatus != null) {
                            tvSyncStatus.setText(message + " (" + current + "%)");
                        }
                    });
                });

                mainHandler.post(() -> {
                    layoutSyncProgress.setVisibility(View.GONE);
                    updateData(result);
                    saveDataToCache(result);
                    NotificationScheduler.schedulePeriodicSync(this);
                    NotificationScheduler.scheduleExerciseReminders(this, activeExercises);
                    if (userTriggered) {
                        Toast.makeText(this, "اطلاعات با موفقیت به‌روزرسانی شد.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    layoutSyncProgress.setVisibility(View.GONE);
                    if (userTriggered) {
                        Toast.makeText(this, "خطا در بروزرسانی: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void updateData(List<Course> courses) {
        courseList.clear();
        activeExercises.clear();
        expiredExercises.clear();

        if (courses != null) {
            courseList.addAll(courses);
            for (Course c : courses) {
                if (c.getExercises() != null) {
                    for (ExerciseItem ex : c.getExercises()) {
                        if (ex.isExpired()) {
                            expiredExercises.add(ex);
                        } else {
                            activeExercises.add(ex);
                        }
                    }
                }
            }
        }

        // Update UI
        activeExerciseAdapter.setItems(activeExercises);
        expiredExerciseAdapter.setItems(expiredExercises);
        courseAccordionAdapter.setCourses(courseList);

        int activeCount = activeExercises.size();
        tvActiveCountBadge.setText(PersianDateHelper.toPersianDigits(activeCount + " تمرین فعال"));
        layoutEmptyActive.setVisibility(activeCount == 0 ? View.VISIBLE : View.GONE);

        int expiredCount = expiredExercises.size();
        tvExpiredCountBadge.setText(PersianDateHelper.toPersianDigits(expiredCount + " منسوخ"));

        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
        tvLastSync.setText("آخرین بروزرسانی: امروز ساعت " + PersianDateHelper.toPersianDigits(sdf.format(new Date(now))));
    }

    private void saveDataToCache(List<Course> courses) {
        try {
            JSONArray arr = new JSONArray();
            for (Course c : courses) {
                arr.put(c.toJson());
            }
            prefs.setCachedData(arr.toString());
            prefs.setLastSyncTime(System.currentTimeMillis());
        } catch (Exception ignored) {}
    }

    private void loadDataFromCache() {
        try {
            String json = prefs.getCachedData();
            if (json.isEmpty()) return;
            JSONArray arr = new JSONArray(json);
            List<Course> cached = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                cached.add(Course.fromJson(arr.getJSONObject(i)));
            }
            updateData(cached);

            long lastTime = prefs.getLastSyncTime();
            if (lastTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);
                tvLastSync.setText("آخرین بروزرسانی: " + PersianDateHelper.toPersianDigits(sdf.format(new Date(lastTime))));
            }
        } catch (Exception ignored) {}
    }

    // --- Action Listeners ---

    @Override
    public void onDownload(ExerciseItem item) {
        if (item.getExGuid() == null || item.getExGuid().trim().isEmpty()) {
            Toast.makeText(this, "این تمرین فایلی برای دریافت ندارد.", Toast.LENGTH_SHORT).show();
            return;
        }

        String filename = "";
        if (!item.getFilePath().isEmpty()) {
            int slash = item.getFilePath().lastIndexOf('/');
            filename = (slash >= 0) ? item.getFilePath().substring(slash + 1) : item.getFilePath();
        }
        if (filename.isEmpty()) {
            filename = item.getTitle();
            if (!filename.toLowerCase().endsWith(".pdf")) {
                filename += ".pdf";
            }
        }
        filename = ElearnClient.sanitizeFilename(filename);

        promptOrDownloadExercise(item, filename, false);
    }

    private void promptOrDownloadExercise(ExerciseItem item, String filename, boolean forceDownload) {
        if (!forceDownload && ElearnClient.isFileDownloaded(this, filename)) {
            showFileExistsDialog(
                    filename,
                    () -> {
                        File existing = ElearnClient.getExistingDownloadedFile(this, filename);
                        if (existing != null) {
                            openDownloadedFile(existing);
                        } else {
                            promptOrDownloadExercise(item, filename, true);
                        }
                    },
                    () -> promptOrDownloadExercise(item, filename, true)
            );
            return;
        }

        Toast.makeText(this, "در حال دانلود فایل تمرین: " + filename + "...", Toast.LENGTH_SHORT).show();
        ElearnClient.getInstance().downloadFile(
                this,
                item.getTcInfoId(),
                item.getExGuid(),
                true,
                filename,
                new ElearnClient.DownloadCallback() {
                    @Override
                    public void onSuccess(File downloadedFile) {
                        mainHandler.post(() -> showDownloadSuccessDialog(downloadedFile));
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
                    }
                }
        );
    }

    @Override
    public void onUpload(ExerciseItem item) {
        showUploadDialog(item);
    }

    @Override
    public void onDownloadFile(Course course, FileItem file) {
        String filename = "";
        if (!file.getFilePath().isEmpty()) {
            int slash = file.getFilePath().lastIndexOf('/');
            filename = (slash >= 0) ? file.getFilePath().substring(slash + 1) : file.getFilePath();
        }
        if (filename.isEmpty()) {
            filename = file.getTitle();
        }
        filename = ElearnClient.sanitizeFilename(filename);

        // For learning files, GetLearnFile takes FGuid
        String guid = (!file.getFGuid().isEmpty()) ? file.getFGuid() : file.getLeGuid();

        promptOrDownloadLearnFile(course.getTcInfoId(), guid, filename, false);
    }

    private void promptOrDownloadLearnFile(String tcInfoId, String guid, String filename, boolean forceDownload) {
        if (!forceDownload && ElearnClient.isFileDownloaded(this, filename)) {
            showFileExistsDialog(
                    filename,
                    () -> {
                        File existing = ElearnClient.getExistingDownloadedFile(this, filename);
                        if (existing != null) {
                            openDownloadedFile(existing);
                        } else {
                            promptOrDownloadLearnFile(tcInfoId, guid, filename, true);
                        }
                    },
                    () -> promptOrDownloadLearnFile(tcInfoId, guid, filename, true)
            );
            return;
        }

        Toast.makeText(this, "در حال دریافت فایل: " + filename + "...", Toast.LENGTH_SHORT).show();
        ElearnClient.getInstance().downloadFile(
                this,
                tcInfoId,
                guid,
                false,
                filename,
                new ElearnClient.DownloadCallback() {
                    @Override
                    public void onSuccess(File downloadedFile) {
                        mainHandler.post(() -> showDownloadSuccessDialog(downloadedFile));
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
                    }
                }
        );
    }

    private void showFileExistsDialog(String filename, Runnable onOpenExisting, Runnable onRedownload) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_exists, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvFilename = dialogView.findViewById(R.id.tv_exists_filename);
        MaterialButton btnOpen = dialogView.findViewById(R.id.btn_exists_open);
        MaterialButton btnRedownload = dialogView.findViewById(R.id.btn_exists_redownload);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_exists_cancel);

        tvFilename.setText(filename);

        btnOpen.setOnClickListener(v -> {
            dialog.dismiss();
            if (onOpenExisting != null) onOpenExisting.run();
        });

        btnRedownload.setOnClickListener(v -> {
            dialog.dismiss();
            if (onRedownload != null) onRedownload.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showDownloadSuccessDialog(File file) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_download_success, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvFilename = dialogView.findViewById(R.id.tv_dialog_filename);
        TextView tvFilepath = dialogView.findViewById(R.id.tv_dialog_filepath);
        MaterialButton btnOpen = dialogView.findViewById(R.id.btn_dialog_open_file);
        MaterialButton btnDismiss = dialogView.findViewById(R.id.btn_dialog_dismiss);

        tvFilename.setText(file.getName());
        tvFilepath.setText("محل ذخیره: پوشه Downloads گوشی");

        btnOpen.setOnClickListener(v -> {
            dialog.dismiss();
            openDownloadedFile(file);
        });

        btnDismiss.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Also show an interactive Snackbar at the bottom
        View root = findViewById(android.R.id.content);
        if (root != null) {
            Snackbar snackbar = Snackbar.make(root, "فایل «" + file.getName() + "» ذخیره شد.", Snackbar.LENGTH_LONG);
            snackbar.setAction("باز کردن فایل", v -> openDownloadedFile(file));
            snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.accent));
            snackbar.show();
        }
    }

    private void openDownloadedFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "فایل یافت نشد یا در دسترس نیست.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            String mimeType = ElearnClient.getMimeType(file.getName());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Intent chooser = new Intent(Intent.ACTION_VIEW);
                chooser.setDataAndType(uri, "*/*");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(chooser, "باز کردن فایل با..."));
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطا در باز کردن فایل: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void styleDialogButtons(AlertDialog dialog) {
        try {
            dialog.setOnShowListener(d -> {
                int accent = ContextCompat.getColor(this, R.color.accent);
                int textPrimary = ContextCompat.getColor(this, R.color.text_primary);
                Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (pos != null) pos.setTextColor(accent);
                if (neu != null) neu.setTextColor(accent);
                if (neg != null) neg.setTextColor(textPrimary);
            });
        } catch (Exception ignored) {}
    }

    // --- Upload Dialog ---

    private void showUploadDialog(ExerciseItem item) {
        currentUploadExercise = item;
        selectedUploadUri = null;
        selectedUploadFilename = null;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_upload, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvCourse = dialogView.findViewById(R.id.tv_upload_course_name);
        TextView tvTitle = dialogView.findViewById(R.id.tv_upload_exercise_title);
        tvUploadSelectedFileName = dialogView.findViewById(R.id.tv_selected_file_name);
        tvUploadSelectedFileSize = dialogView.findViewById(R.id.tv_selected_file_size);
        MaterialButton btnChooseFile = dialogView.findViewById(R.id.btn_choose_file);
        EditText etDesc = dialogView.findViewById(R.id.et_upload_desc);
        btnSubmitUpload = dialogView.findViewById(R.id.btn_submit_upload);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_upload);
        pbUploading = dialogView.findViewById(R.id.pb_uploading);

        tvCourse.setText(item.getCourseName());
        tvTitle.setText(item.getTitle());

        btnChooseFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmitUpload.setOnClickListener(v -> {
            if (selectedUploadUri == null) {
                Toast.makeText(this, "لطفاً ابتدا یک فایل را انتخاب کنید.", Toast.LENGTH_SHORT).show();
                return;
            }

            pbUploading.setVisibility(View.VISIBLE);
            btnSubmitUpload.setEnabled(false);
            String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";

            ElearnClient.getInstance().uploadAssignment(
                    this,
                    item.getTcInfoId(),
                    item.getExid(),
                    prefs.getUsername(),
                    selectedUploadUri,
                    selectedUploadFilename != null ? selectedUploadFilename : "answer.pdf",
                    desc,
                    new ElearnClient.UploadCallback() {
                        @Override
                        public void onSuccess(String message) {
                            mainHandler.post(() -> {
                                pbUploading.setVisibility(View.GONE);
                                btnSubmitUpload.setEnabled(true);
                                dialog.dismiss();
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            mainHandler.post(() -> {
                                pbUploading.setVisibility(View.GONE);
                                btnSubmitUpload.setEnabled(true);
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
            );
        });

        dialog.show();
    }

    private void handleSelectedFile(Uri uri) {
        String filename = "file";
        long size = 0;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex != -1) filename = cursor.getString(nameIndex);
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}

        selectedUploadFilename = filename;
        if (tvUploadSelectedFileName != null) {
            tvUploadSelectedFileName.setText(filename);
            double sizeMb = size / (1024.0 * 1024.0);
            tvUploadSelectedFileSize.setText(String.format(Locale.US, "حجم: %.2f مگابایت", sizeMb));
        }
    }

    // --- Settings Dialog ---

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvUser = dialogView.findViewById(R.id.tv_settings_username);
        MaterialButton btnLogout = dialogView.findViewById(R.id.btn_settings_logout);
        RadioGroup rgTheme = dialogView.findViewById(R.id.rg_theme);
        RadioButton rbSystem = dialogView.findViewById(R.id.rb_theme_system);
        RadioButton rbLight = dialogView.findViewById(R.id.rb_theme_light);
        RadioButton rbDark = dialogView.findViewById(R.id.rb_theme_dark);

        SwitchMaterial switchSync = dialogView.findViewById(R.id.switch_sync_enabled);
        Spinner spinnerInterval = dialogView.findViewById(R.id.spinner_sync_interval);

        CheckBox cbScan = dialogView.findViewById(R.id.cb_notif_scan);
        CheckBox cbWeekly = dialogView.findViewById(R.id.cb_notif_weekly);
        CheckBox cbDueDay = dialogView.findViewById(R.id.cb_notif_due_day);
        CheckBox cb3h = dialogView.findViewById(R.id.cb_notif_3h);
        CheckBox cb1h = dialogView.findViewById(R.id.cb_notif_1h);
        CheckBox cb15m = dialogView.findViewById(R.id.cb_notif_15m);

        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_settings);

        tvUser.setText(prefs.getUsername());

        // Theme selection
        int curTheme = prefs.getThemeMode();
        if (curTheme == AppPreferences.THEME_LIGHT) rbLight.setChecked(true);
        else if (curTheme == AppPreferences.THEME_DARK) rbDark.setChecked(true);
        else rbSystem.setChecked(true);

        // Sync switch & spinner
        switchSync.setChecked(prefs.isSyncEnabled());
        String[] intervals = new String[]{"هر ۱ ساعت", "هر ۲ ساعت", "هر ۳ ساعت", "هر ۶ ساعت", "هر ۱۲ ساعت", "هر ۲۴ ساعت"};
        int[] intervalValues = new int[]{1, 2, 3, 6, 12, 24};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, intervals);
        spinnerInterval.setAdapter(adapter);

        int curHours = prefs.getSyncIntervalHours();
        int selIdx = 2; // default 3h
        for (int i = 0; i < intervalValues.length; i++) {
            if (intervalValues[i] == curHours) {
                selIdx = i;
                break;
            }
        }
        spinnerInterval.setSelection(selIdx);

        // Notification toggles (defaults true)
        cbScan.setChecked(prefs.isNotifOnScan());
        cbWeekly.setChecked(prefs.isNotifWeekly());
        cbDueDay.setChecked(prefs.isNotifDueDay());
        cb3h.setChecked(prefs.isNotif3Hours());
        cb1h.setChecked(prefs.isNotif1Hour());
        cb15m.setChecked(prefs.isNotif15Min());

        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            prefs.setLoggedIn(false);
            prefs.clearAll();
            layoutLogin.setVisibility(View.VISIBLE);
            courseList.clear();
            activeExercises.clear();
            expiredExercises.clear();
            activeExerciseAdapter.setItems(null);
            expiredExerciseAdapter.setItems(null);
            courseAccordionAdapter.setCourses(null);
            NotificationScheduler.cancelPeriodicSync(this);
            Toast.makeText(this, "از حساب کاربری خارج شدید.", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            // Theme
            int chosenTheme = AppPreferences.THEME_SYSTEM;
            if (rbLight.isChecked()) chosenTheme = AppPreferences.THEME_LIGHT;
            else if (rbDark.isChecked()) chosenTheme = AppPreferences.THEME_DARK;
            prefs.setThemeMode(chosenTheme);

            // Sync
            prefs.setSyncEnabled(switchSync.isChecked());
            int chosenInterval = intervalValues[spinnerInterval.getSelectedItemPosition()];
            prefs.setSyncIntervalHours(chosenInterval);

            // Notification options
            prefs.setNotifOnScan(cbScan.isChecked());
            prefs.setNotifWeekly(cbWeekly.isChecked());
            prefs.setNotifDueDay(cbDueDay.isChecked());
            prefs.setNotif3Hours(cb3h.isChecked());
            prefs.setNotif1Hour(cb1h.isChecked());
            prefs.setNotif15Min(cb15m.isChecked());

            NotificationScheduler.schedulePeriodicSync(this);
            NotificationScheduler.scheduleExerciseReminders(this, activeExercises);

            dialog.dismiss();
            Toast.makeText(this, "تنظیمات با موفقیت ذخیره شد.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }
}
