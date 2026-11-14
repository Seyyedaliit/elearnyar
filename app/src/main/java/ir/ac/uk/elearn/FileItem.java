package ir.ac.uk.elearn;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;

public class FileItem implements Serializable {

    private String lid;
    private int fid;
    private String title;
    private String description;
    private String insertDate;
    private String fGuid;
    private String leGuid;
    private String filePath;
    private String courseName;
    private String tcInfoId;

    public FileItem() {}

    public String getLid() { return lid != null ? lid : ""; }
    public void setLid(String lid) { this.lid = lid; }

    public int getFid() { return fid; }
    public void setFid(int fid) { this.fid = fid; }

    public String getTitle() { return title != null ? title : "فایل آموزشی بدون عنوان"; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public String getInsertDate() { return insertDate != null ? insertDate : ""; }
    public void setInsertDate(String insertDate) { this.insertDate = insertDate; }

    public String getFGuid() { return fGuid != null ? fGuid : ""; }
    public void setFGuid(String fGuid) { this.fGuid = fGuid; }

    public String getLeGuid() { return leGuid != null ? leGuid : ""; }
    public void setLeGuid(String leGuid) { this.leGuid = leGuid; }

    public String getFilePath() { return filePath != null ? filePath : ""; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getCourseName() { return courseName != null ? courseName : ""; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getTcInfoId() { return tcInfoId != null ? tcInfoId : ""; }
    public void setTcInfoId(String tcInfoId) { this.tcInfoId = tcInfoId; }

    public String getPersianDate() {
        return PersianDateHelper.toPersianDate(insertDate);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("lid", lid);
        obj.put("fid", fid);
        obj.put("title", title);
        obj.put("description", description);
        obj.put("insertDate", insertDate);
        obj.put("fGuid", fGuid);
        obj.put("leGuid", leGuid);
        obj.put("filePath", filePath);
        obj.put("courseName", courseName);
        obj.put("tcInfoId", tcInfoId);
        return obj;
    }

    public static FileItem fromJson(JSONObject obj) {
        FileItem item = new FileItem();
        item.lid = obj.optString("lid", "");
        item.fid = obj.optInt("fid", 0);
        item.title = obj.optString("title", "");
        item.description = obj.optString("description", "");
        item.insertDate = obj.optString("insertDate", "");
        item.fGuid = obj.optString("fGuid", "");
        item.leGuid = obj.optString("leGuid", "");
        item.filePath = obj.optString("filePath", "");
        item.courseName = obj.optString("courseName", "");
        item.tcInfoId = obj.optString("tcInfoId", "");
        return item;
    }
}
