package ir.ac.uk.elearn;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ElearnClient {

    private static final String BASE_URL = "https://elearn.uk.ac.ir";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 25000;

    private static ElearnClient instance;
    private final CookieManager cookieManager;

    public interface ProgressListener {
        void onProgress(String message, int current, int total);
    }

    public interface DownloadCallback {
        void onSuccess(File downloadedFile);
        void onError(String message);
    }

    public interface UploadCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private ElearnClient() {
        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        trustAllCertificates();
    }

    public static synchronized ElearnClient getInstance() {
        if (instance == null) {
            instance = new ElearnClient();
        }
        return instance;
    }

    private void trustAllCertificates() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception ignored) {}
    }

    public boolean login(String username, String password) throws Exception {
        // 1. Visit index.aspx to establish session
        HttpURLConnection conn1 = createConnection("/index.aspx", "GET");
        conn1.connect();
        int code1 = conn1.getResponseCode();
        readStreamAndClose(conn1.getInputStream());
        if (code1 != 200) {
            throw new Exception("خطا در برقراری ارتباط با سامانه elearn (کد: " + code1 + ")");
        }

        // 2. Post login credentials
        JSONObject loginJson = new JSONObject();
        loginJson.put("valuser", username);
        loginJson.put("valpass", password);
        loginJson.put("setpass", "0");
        byte[] body = loginJson.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn2 = createConnection("/Index.aspx/get_login", "POST");
        conn2.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn2.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        conn2.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn2.setRequestProperty("Referer", BASE_URL + "/index.aspx");
        conn2.setDoOutput(true);

        try (OutputStream os = conn2.getOutputStream()) {
            os.write(body);
        }

        int code2 = conn2.getResponseCode();
        if (code2 != 200) {
            throw new Exception("پاسخ نامعتبر از سرور (کد: " + code2 + ")");
        }

        String respStr = readStreamAndClose(conn2.getInputStream());
        JSONObject respObj = new JSONObject(respStr);
        JSONObject d = respObj.optJSONObject("d");
        if (d != null && "ok".equals(d.optString("Item1"))) {
            return true;
        } else {
            throw new Exception("نام کاربری یا کلمه عبور اشتباه است.");
        }
    }

    public List<Course> fetchAllData(String username, String password, ProgressListener listener) throws Exception {
        if (listener != null) listener.onProgress("در حال ورود به سامانه...", 0, 100);
        login(username, password);

        // Visit STClassList.aspx
        if (listener != null) listener.onProgress("در حال واکشی لیست دروس...", 10, 100);
        HttpURLConnection connClassPage = createConnection("/STClassList.aspx", "GET");
        connClassPage.setRequestProperty("Referer", BASE_URL + "/index.aspx");
        readStreamAndClose(connClassPage.getInputStream());

        // Get STClasses list
        HttpURLConnection connCourses = createConnection("/STClasslist.aspx/GetSTClasses", "GET");
        connCourses.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connCourses.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        connCourses.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        connCourses.setRequestProperty("Referer", BASE_URL + "/STClassList.aspx");

        String coursesRaw = readStreamAndClose(connCourses.getInputStream());
        JSONObject coursesRoot = new JSONObject(coursesRaw);
        String coursesJsonStr = coursesRoot.getString("d");
        JSONArray coursesArray = new JSONArray(coursesJsonStr);

        List<Course> courses = new ArrayList<>();
        int total = coursesArray.length();

        for (int i = 0; i < total; i++) {
            JSONObject cObj = coursesArray.getJSONObject(i);
            Course c = new Course();
            c.setTcInfoId(cObj.optString("TCInfoID", ""));
            c.setName(cObj.optString("classname", ""));
            c.setGroupNo(cObj.optString("LessonGroupNo", ""));
            c.setUnitType(cObj.optInt("unittype", 1));
            c.setTeacherName(cObj.optString("TeacherName", ""));
            c.setExercisesCount(cObj.optInt("Exercisecount", 0));
            c.setLearnCount(cObj.optInt("learncount", 0));
            c.setUnreadMessages(cObj.optInt("messagecount", 0));
            courses.add(c);
        }

        // Now for each course, fetch details, exercises, and files
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            int progress = 10 + (int) (((i + 1) / (float) total) * 85);
            if (listener != null) {
                listener.onProgress("دریافت فایل‌ها و تمرین‌های درس " + course.getName() + "...", progress, 100);
            }
            try {
                fetchCourseDetails(course);
            } catch (Exception e) {
                // Keep going for other courses
            }
        }

        if (listener != null) listener.onProgress("اطلاعات با موفقیت به‌روزرسانی شد.", 100, 100);
        return courses;
    }

    private void fetchCourseDetails(Course course) throws Exception {
        String courseUrl = BASE_URL + "/STCoursepage.aspx?CLid=" + URLEncoder.encode(course.getTcInfoId(), "UTF-8");

        // 1. Visit STCoursepage.aspx
        HttpURLConnection connPage = createConnection("/STCoursepage.aspx?CLid=" + URLEncoder.encode(course.getTcInfoId(), "UTF-8"), "GET");
        readStreamAndClose(connPage.getInputStream());

        // 2. Call GetInfo to bind course to session
        HttpURLConnection connInfo = createConnection("/STCoursepage.aspx/GetInfo", "POST");
        connInfo.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connInfo.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        connInfo.setRequestProperty("Referer", courseUrl);
        connInfo.setDoOutput(true);
        try (OutputStream os = connInfo.getOutputStream()) {
            os.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        String infoRaw = readStreamAndClose(connInfo.getInputStream());
        try {
            JSONObject infoObj = new JSONObject(infoRaw);
            String infoJsonStr = infoObj.getString("d");
            JSONArray infoArr = new JSONArray(infoJsonStr);
            if (infoArr.length() > 0) {
                JSONObject first = infoArr.getJSONObject(0);
                course.setTeacherName(first.optString("teachername", course.getTeacherName()));
                course.setExamDate(first.optString("ExamPersianDate", ""));
                course.setExamTime(first.optString("ExamStartTime", ""));
            }
        } catch (Exception ignored) {}

        // 3. ExerciseFileList
        HttpURLConnection connEx = createConnection("/STCoursepage.aspx/ExerciseFileList", "POST");
        connEx.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connEx.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        connEx.setRequestProperty("Referer", courseUrl);
        connEx.setDoOutput(true);
        try (OutputStream os = connEx.getOutputStream()) {
            os.write("{\"startpage\":\"0\",\"rowcount\":\"50\"}".getBytes(StandardCharsets.UTF_8));
        }
        String exRaw = readStreamAndClose(connEx.getInputStream());
        try {
            JSONObject exObj = new JSONObject(exRaw);
            String exJsonStr = exObj.getString("d");
            JSONObject exRoot = new JSONObject(exJsonStr);
            JSONArray aaData = exRoot.optJSONArray("aaData");
            if (aaData != null) {
                List<ExerciseItem> exercises = new ArrayList<>();
                for (int j = 0; j < aaData.length(); j++) {
                    JSONObject row = aaData.getJSONObject(j);
                    ExerciseItem ex = new ExerciseItem();
                    ex.setExid(String.valueOf(row.optInt("exid", 0)));
                    ex.setFid(row.optInt("fid", 0));
                    ex.setTitle(row.optString("extitle", ""));
                    ex.setDescription(row.optString("exDescription", ""));
                    ex.setStartDate(row.optString("StartDate", ""));
                    ex.setEndDate(row.optString("EndDate", ""));
                    ex.setGrade(row.optDouble("Grade", 0.0));
                    ex.setExGuid(row.optString("ExGuid", ""));
                    ex.setFGuid(row.optString("FGuid", ""));
                    ex.setFilePath(row.optString("lfilepath", ""));
                    ex.setCourseName(course.getName());
                    ex.setTcInfoId(course.getTcInfoId());
                    exercises.add(ex);
                }
                course.setExercises(exercises);
            }
        } catch (Exception ignored) {}

        // 4. LearningFileList
        HttpURLConnection connLearn = createConnection("/STCoursepage.aspx/LearningFileList", "POST");
        connLearn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connLearn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        connLearn.setRequestProperty("Referer", courseUrl);
        connLearn.setDoOutput(true);
        try (OutputStream os = connLearn.getOutputStream()) {
            os.write("{\"startpage\":\"0\",\"rowcount\":\"50\"}".getBytes(StandardCharsets.UTF_8));
        }
        String learnRaw = readStreamAndClose(connLearn.getInputStream());
        try {
            JSONObject learnObj = new JSONObject(learnRaw);
            String learnJsonStr = learnObj.getString("d");
            JSONObject learnRoot = new JSONObject(learnJsonStr);
            JSONArray aaData = learnRoot.optJSONArray("aaData");
            if (aaData != null) {
                List<FileItem> files = new ArrayList<>();
                for (int j = 0; j < aaData.length(); j++) {
                    JSONObject row = aaData.getJSONObject(j);
                    FileItem fi = new FileItem();
                    fi.setLid(String.valueOf(row.optInt("lid", 0)));
                    fi.setFid(row.optInt("fid", 0));
                    fi.setTitle(row.optString("ltitle", ""));
                    fi.setDescription(row.optString("lDescription", ""));
                    fi.setInsertDate(row.optString("insertdate", ""));
                    fi.setFGuid(row.optString("FGuid", ""));
                    fi.setLeGuid(row.optString("LeGuid", ""));
                    fi.setFilePath(row.optString("lfilepath", ""));
                    fi.setCourseName(course.getName());
                    fi.setTcInfoId(course.getTcInfoId());
                    files.add(fi);
                }
                course.setLearningFiles(files);
            }
        } catch (Exception ignored) {}
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null) return "downloaded_file";
        String clean = filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) return "downloaded_file";
        return clean;
    }

    public static String getMimeType(String filename) {
        if (filename == null) return "*/*";
        String extension = "";
        int i = filename.lastIndexOf('.');
        if (i > 0 && i < filename.length() - 1) {
            extension = filename.substring(i + 1).toLowerCase();
        }
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mime != null) return mime;
        if ("pdf".equals(extension)) return "application/pdf";
        if ("zip".equals(extension)) return "application/zip";
        if ("rar".equals(extension)) return "application/x-rar-compressed";
        if ("docx".equals(extension)) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if ("doc".equals(extension)) return "application/msword";
        if ("pptx".equals(extension)) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if ("ppt".equals(extension)) return "application/vnd.ms-powerpoint";
        if ("xlsx".equals(extension)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if ("xls".equals(extension)) return "application/vnd.ms-excel";
        if ("jpg".equals(extension) || "jpeg".equals(extension)) return "image/jpeg";
        if ("png".equals(extension)) return "image/png";
        if ("txt".equals(extension)) return "text/plain";
        return "*/*";
    }

    public static boolean isFileDownloaded(Context context, String filename) {
        if (filename == null || filename.trim().isEmpty()) return false;
        String clean = sanitizeFilename(filename);

        File appFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), clean);
        if (appFile.exists() && appFile.length() > 0) return true;

        File pubFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), clean);
        if (pubFile.exists() && pubFile.length() > 0) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + " = ?",
                    new String[]{clean},
                    null)) {
                if (cursor != null && cursor.moveToFirst()) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static File getExistingDownloadedFile(Context context, String filename) {
        if (filename == null || filename.trim().isEmpty()) return null;
        String clean = sanitizeFilename(filename);

        File appFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), clean);
        if (appFile.exists() && appFile.length() > 0) return appFile;

        File pubFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), clean);
        if (pubFile.exists() && pubFile.length() > 0) return pubFile;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + " = ?",
                    new String[]{clean},
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                    try (InputStream in = context.getContentResolver().openInputStream(contentUri);
                         OutputStream out = new FileOutputStream(appFile)) {
                        if (in != null) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                            }
                            out.flush();
                        }
                    }
                    if (appFile.exists() && appFile.length() > 0) return appFile;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static File saveBytesToStorage(Context context, byte[] data, String filename) throws Exception {
        String cleanName = sanitizeFilename(filename);
        String mimeType = getMimeType(cleanName);

        File appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (appDir != null && !appDir.exists()) {
            appDir.mkdirs();
        }
        File appFile = new File(appDir, cleanName);
        try (FileOutputStream fos = new FileOutputStream(appFile)) {
            fos.write(data);
            fos.flush();
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                try {
                    resolver.delete(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            MediaStore.Downloads.DISPLAY_NAME + " = ?",
                            new String[]{cleanName}
                    );
                } catch (Exception ignored) {}

                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, cleanName);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = resolver.openOutputStream(uri)) {
                        if (os != null) {
                            os.write(data);
                            os.flush();
                        }
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                }
            } else {
                File pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (pubDir != null) {
                    if (!pubDir.exists()) pubDir.mkdirs();
                    File pubFile = new File(pubDir, cleanName);
                    try (FileOutputStream fos = new FileOutputStream(pubFile)) {
                        fos.write(data);
                        fos.flush();
                    }
                    MediaScannerConnection.scanFile(context, new String[]{pubFile.getAbsolutePath()}, new String[]{mimeType}, null);
                }
            }
        } catch (Exception ignored) {}

        return appFile;
    }

    public void downloadFile(Context context, String tcInfoId, String guid, boolean isExercise, String suggestedFilename, DownloadCallback callback) {
        new Thread(() -> performDownload(context, tcInfoId, guid, isExercise, suggestedFilename, callback, false)).start();
    }

    private void performDownload(Context context, String tcInfoId, String guid, boolean isExercise, String suggestedFilename, DownloadCallback callback, boolean isRetry) {
        try {
            AppPreferences prefs = AppPreferences.getInstance(context);

            if (guid == null || guid.trim().isEmpty()) {
                if (callback != null) {
                    callback.onError(isExercise ? "این تمرین فایلی برای دریافت ندارد." : "شناسه فایل یافت نشد.");
                }
                return;
            }

            if (cookieManager.getCookieStore().getCookies().isEmpty() && prefs.isLoggedIn()) {
                login(prefs.getUsername(), prefs.getPassword());
            }

            String courseUrl = BASE_URL + "/STCoursepage.aspx?CLid=" + URLEncoder.encode(tcInfoId, "UTF-8");

            HttpURLConnection connPage = createConnection("/STCoursepage.aspx?CLid=" + URLEncoder.encode(tcInfoId, "UTF-8"), "GET");
            readStreamAndClose(connPage.getInputStream());

            HttpURLConnection connInfo = createConnection("/STCoursepage.aspx/GetInfo", "POST");
            connInfo.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connInfo.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connInfo.setRequestProperty("Referer", courseUrl);
            connInfo.setDoOutput(true);
            try (OutputStream os = connInfo.getOutputStream()) {
                os.write("{}".getBytes(StandardCharsets.UTF_8));
            }
            readStreamAndClose(connInfo.getInputStream());

            String endpoint = isExercise ? "/STCoursepage.aspx/GetExFile" : "/STCoursepage.aspx/GetLearnFile";
            JSONObject param = new JSONObject();
            if (isExercise) {
                param.put("ExcId", guid);
            } else {
                param.put("LeId", guid);
            }

            HttpURLConnection connPrep = createConnection(endpoint, "POST");
            connPrep.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connPrep.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connPrep.setRequestProperty("Referer", courseUrl);
            connPrep.setDoOutput(true);
            try (OutputStream os = connPrep.getOutputStream()) {
                os.write(param.toString().getBytes(StandardCharsets.UTF_8));
            }

            int prepCode = connPrep.getResponseCode();
            if (prepCode != 200) {
                if (!isRetry && prefs.isLoggedIn()) {
                    login(prefs.getUsername(), prefs.getPassword());
                    performDownload(context, tcInfoId, guid, isExercise, suggestedFilename, callback, true);
                    return;
                }
                throw new Exception("خطا در برقراری ارتباط با سرور (کد: " + prepCode + ")");
            }

            String prepResp = readStreamAndClose(connPrep.getInputStream());
            if (prepResp.contains("nodate")) {
                if (callback != null) callback.onError("پیش از شروع مهلت، امکان دریافت فایل وجود ندارد.");
                return;
            }
            if (prepResp.contains("get_login") || prepResp.startsWith("<!DOCTYPE")) {
                if (!isRetry && prefs.isLoggedIn()) {
                    login(prefs.getUsername(), prefs.getPassword());
                    performDownload(context, tcInfoId, guid, isExercise, suggestedFilename, callback, true);
                    return;
                }
            }

            HttpURLConnection connDl = createConnection("/DownLoad.ashx", "GET");
            connDl.setRequestProperty("Referer", courseUrl);
            int dlCode = connDl.getResponseCode();
            if (dlCode != 200) {
                if (!isRetry && prefs.isLoggedIn()) {
                    login(prefs.getUsername(), prefs.getPassword());
                    performDownload(context, tcInfoId, guid, isExercise, suggestedFilename, callback, true);
                    return;
                }
                throw new Exception("خطا در دانلود فایل (کد: " + dlCode + ")");
            }

            String disp = connDl.getHeaderField("Content-Disposition");
            String filename = suggestedFilename;
            if (disp != null && disp.contains("filename=")) {
                int idx = disp.indexOf("filename=");
                String rawName = disp.substring(idx + 9).replace("\"", "").trim();
                try {
                    filename = new String(rawName.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    filename = rawName;
                }
            }
            if (filename == null || filename.trim().isEmpty()) {
                filename = "elearn_file_" + System.currentTimeMillis() + (isExercise ? ".pdf" : ".dat");
            }
            filename = sanitizeFilename(filename);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = new BufferedInputStream(connDl.getInputStream())) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
            byte[] fileBytes = baos.toByteArray();
            if (fileBytes.length == 0) {
                throw new Exception("فایل دریافت شده خالی است.");
            }

            File targetFile = saveBytesToStorage(context, fileBytes, filename);

            if (callback != null) {
                callback.onSuccess(targetFile);
            }

        } catch (Exception e) {
            if (!isRetry && AppPreferences.getInstance(context).isLoggedIn()) {
                try {
                    login(AppPreferences.getInstance(context).getUsername(), AppPreferences.getInstance(context).getPassword());
                    performDownload(context, tcInfoId, guid, isExercise, suggestedFilename, callback, true);
                    return;
                } catch (Exception ignored) {}
            }
            if (callback != null) {
                callback.onError("خطا در دریافت فایل: " + e.getMessage());
            }
        }
    }

    public void uploadAssignment(Context context, String tcInfoId, String excId, String studentNo, Uri fileUri, String filename, String desc, UploadCallback callback) {
        new Thread(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                if (cookieManager.getCookieStore().getCookies().isEmpty() && prefs.isLoggedIn()) {
                    login(prefs.getUsername(), prefs.getPassword());
                }

                String courseUrl = BASE_URL + "/STCoursepage.aspx?CLid=" + URLEncoder.encode(tcInfoId, "UTF-8");

                // Bind course session
                HttpURLConnection connPage = createConnection("/STCoursepage.aspx?CLid=" + URLEncoder.encode(tcInfoId, "UTF-8"), "GET");
                readStreamAndClose(connPage.getInputStream());

                HttpURLConnection connInfo = createConnection("/STCoursepage.aspx/GetInfo", "POST");
                connInfo.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connInfo.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                connInfo.setRequestProperty("Referer", courseUrl);
                connInfo.setDoOutput(true);
                try (OutputStream os = connInfo.getOutputStream()) {
                    os.write("{}".getBytes(StandardCharsets.UTF_8));
                }
                readStreamAndClose(connInfo.getInputStream());

                // Read file bytes from Uri
                ContentResolver cr = context.getContentResolver();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream is = cr.openInputStream(fileUri)) {
                    if (is == null) throw new Exception("امکان خواندن فایل انتخاب شده وجود ندارد.");
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                }
                byte[] fileBytes = baos.toByteArray();
                long fileSize = fileBytes.length;

                // 1. Multipart POST to UploadAnswer.ashx?ExcId=...&&StNu=...
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                String uploadUrl = "/UploadAnswer.ashx?ExcId=" + excId + "&&StNu=" + studentNo;
                HttpURLConnection connUp = createConnection(uploadUrl, "POST");
                connUp.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connUp.setRequestProperty("Referer", courseUrl);
                connUp.setDoOutput(true);

                try (DataOutputStream dos = new DataOutputStream(connUp.getOutputStream())) {
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
                    dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
                    dos.write(fileBytes);
                    dos.writeBytes("\r\n");
                    dos.writeBytes("--" + boundary + "--\r\n");
                    dos.flush();
                }

                int upCode = connUp.getResponseCode();
                readStreamAndClose(connUp.getInputStream());
                if (upCode != 200) {
                    throw new Exception("خطا در آپلود فایل (کد: " + upCode + ")");
                }

                // 2. Call SaveAnswer
                JSONObject saveJson = new JSONObject();
                saveJson.put("ExcId", excId);
                saveJson.put("ExcDesc", desc != null ? desc : "");
                saveJson.put("FileName", filename);
                saveJson.put("FileType", filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "");
                saveJson.put("FileSize", String.valueOf(fileSize));
                saveJson.put("IsEdit", "false");

                HttpURLConnection connSave = createConnection("/STCoursepage.aspx/SaveAnswer", "POST");
                connSave.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connSave.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                connSave.setRequestProperty("Referer", courseUrl);
                connSave.setDoOutput(true);
                try (OutputStream os = connSave.getOutputStream()) {
                    os.write(saveJson.toString().getBytes(StandardCharsets.UTF_8));
                }

                String saveResp = readStreamAndClose(connSave.getInputStream());
                if (saveResp.contains("oksave") || saveResp.contains("okedit")) {
                    if (callback != null) callback.onSuccess("پاسخ تمرین با موفقیت ارسال شد.");
                } else {
                    if (callback != null) callback.onError("خطا در ثبت پاسخ: " + saveResp);
                }

            } catch (Exception e) {
                if (callback != null) callback.onError("خطا در ارسال تمرین: " + e.getMessage());
            }
        }).start();
    }

    private HttpURLConnection createConnection(String path, String method) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private String readStreamAndClose(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
