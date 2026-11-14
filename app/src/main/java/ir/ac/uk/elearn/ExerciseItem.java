package ir.ac.uk.elearn;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;

public class ExerciseItem implements Serializable {

    private String exid;
    private int fid;
    private String title;
    private String description;
    private String startDate;
    private String endDate;
    private double grade;
    private String exGuid;
    private String fGuid;
    private String filePath;
    private String courseName;
    private String tcInfoId;

    public ExerciseItem() {}

    public String getExid() { return exid != null ? exid : ""; }
    public void setExid(String exid) { this.exid = exid; }

    public int getFid() { return fid; }
    public void setFid(int fid) { this.fid = fid; }

    public String getTitle() { return title != null ? title : "تمرین بدون عنوان"; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public String getStartDate() { return startDate != null ? startDate : ""; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate != null ? endDate : ""; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public double getGrade() { return grade; }
    public void setGrade(double grade) { this.grade = grade; }

    public String getExGuid() { return exGuid != null ? exGuid : ""; }
    public void setExGuid(String exGuid) { this.exGuid = exGuid; }

    public String getFGuid() { return fGuid != null ? fGuid : ""; }
    public void setFGuid(String fGuid) { this.fGuid = fGuid; }

    public String getFilePath() { return filePath != null ? filePath : ""; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getCourseName() { return courseName != null ? courseName : ""; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getTcInfoId() { return tcInfoId != null ? tcInfoId : ""; }
    public void setTcInfoId(String tcInfoId) { this.tcInfoId = tcInfoId; }

    public boolean isExpired() {
        return PersianDateHelper.isExpired(endDate);
    }

    public String getRemainingText() {
        return PersianDateHelper.getRemainingTime(endDate);
    }

    public String getPersianDeadline() {
        return PersianDateHelper.toPersianDate(endDate);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("exid", exid);
        obj.put("fid", fid);
        obj.put("title", title);
        obj.put("description", description);
        obj.put("startDate", startDate);
        obj.put("endDate", endDate);
        obj.put("grade", grade);
        obj.put("exGuid", exGuid);
        obj.put("fGuid", fGuid);
        obj.put("filePath", filePath);
        obj.put("courseName", courseName);
        obj.put("tcInfoId", tcInfoId);
        return obj;
    }

    public static ExerciseItem fromJson(JSONObject obj) {
        ExerciseItem item = new ExerciseItem();
        item.exid = obj.optString("exid", "");
        item.fid = obj.optInt("fid", 0);
        item.title = obj.optString("title", "");
        item.description = obj.optString("description", "");
        item.startDate = obj.optString("startDate", "");
        item.endDate = obj.optString("endDate", "");
        item.grade = obj.optDouble("grade", 0.0);
        item.exGuid = obj.optString("exGuid", "");
        item.fGuid = obj.optString("fGuid", "");
        item.filePath = obj.optString("filePath", "");
        item.courseName = obj.optString("courseName", "");
        item.tcInfoId = obj.optString("tcInfoId", "");
        return item;
    }
}
