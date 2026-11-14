package ir.ac.uk.elearn;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Course implements Serializable {

    private String tcInfoId;
    private String name;
    private String groupNo;
    private int unitType; // 1 = theory, 2 = practical
    private String teacherName;
    private String examDate;
    private String examTime;
    private int exercisesCount;
    private int learnCount;
    private int unreadMessages;

    private List<ExerciseItem> exercises = new ArrayList<>();
    private List<FileItem> learningFiles = new ArrayList<>();

    public Course() {}

    public Course(String tcInfoId, String name, String groupNo, int unitType) {
        this.tcInfoId = tcInfoId;
        this.name = name;
        this.groupNo = groupNo;
        this.unitType = unitType;
    }

    public String getTcInfoId() { return tcInfoId; }
    public void setTcInfoId(String tcInfoId) { this.tcInfoId = tcInfoId; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getGroupNo() { return groupNo != null ? groupNo : ""; }
    public void setGroupNo(String groupNo) { this.groupNo = groupNo; }

    public int getUnitType() { return unitType; }
    public void setUnitType(int unitType) { this.unitType = unitType; }

    public String getTeacherName() { return teacherName != null ? teacherName : "نامشخص"; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getExamDate() { return examDate != null ? examDate : ""; }
    public void setExamDate(String examDate) { this.examDate = examDate; }

    public String getExamTime() { return examTime != null ? examTime : ""; }
    public void setExamTime(String examTime) { this.examTime = examTime; }

    public int getExercisesCount() { return exercisesCount; }
    public void setExercisesCount(int exercisesCount) { this.exercisesCount = exercisesCount; }

    public int getLearnCount() { return learnCount; }
    public void setLearnCount(int learnCount) { this.learnCount = learnCount; }

    public int getUnreadMessages() { return unreadMessages; }
    public void setUnreadMessages(int unreadMessages) { this.unreadMessages = unreadMessages; }

    public List<ExerciseItem> getExercises() { return exercises; }
    public void setExercises(List<ExerciseItem> exercises) { this.exercises = exercises; }

    public List<FileItem> getLearningFiles() { return learningFiles; }
    public void setLearningFiles(List<FileItem> learningFiles) { this.learningFiles = learningFiles; }

    public int getActiveExercisesCount() {
        int count = 0;
        for (ExerciseItem ex : exercises) {
            if (!ex.isExpired()) count++;
        }
        return count;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("tcInfoId", tcInfoId);
        obj.put("name", name);
        obj.put("groupNo", groupNo);
        obj.put("unitType", unitType);
        obj.put("teacherName", teacherName);
        obj.put("examDate", examDate);
        obj.put("examTime", examTime);
        obj.put("exercisesCount", exercisesCount);
        obj.put("learnCount", learnCount);
        obj.put("unreadMessages", unreadMessages);

        JSONArray exArr = new JSONArray();
        for (ExerciseItem ex : exercises) {
            exArr.put(ex.toJson());
        }
        obj.put("exercises", exArr);

        JSONArray fileArr = new JSONArray();
        for (FileItem f : learningFiles) {
            fileArr.put(f.toJson());
        }
        obj.put("learningFiles", fileArr);

        return obj;
    }

    public static Course fromJson(JSONObject obj) {
        Course c = new Course();
        c.tcInfoId = obj.optString("tcInfoId", "");
        c.name = obj.optString("name", "");
        c.groupNo = obj.optString("groupNo", "");
        c.unitType = obj.optInt("unitType", 1);
        c.teacherName = obj.optString("teacherName", "");
        c.examDate = obj.optString("examDate", "");
        c.examTime = obj.optString("examTime", "");
        c.exercisesCount = obj.optInt("exercisesCount", 0);
        c.learnCount = obj.optInt("learnCount", 0);
        c.unreadMessages = obj.optInt("unreadMessages", 0);

        JSONArray exArr = obj.optJSONArray("exercises");
        if (exArr != null) {
            for (int i = 0; i < exArr.length(); i++) {
                JSONObject o = exArr.optJSONObject(i);
                if (o != null) c.exercises.add(ExerciseItem.fromJson(o));
            }
        }

        JSONArray fArr = obj.optJSONArray("learningFiles");
        if (fArr != null) {
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.optJSONObject(i);
                if (o != null) c.learningFiles.add(FileItem.fromJson(o));
            }
        }

        return c;
    }
}
