package ir.ac.uk.elearn;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {

    private final Context context;
    private final List<Course> courses = new ArrayList<>();
    private final Set<String> expandedCourseIds = new HashSet<>();

    public AttendanceAdapter(Context context) {
        this.context = context;
    }

    public void setCourses(List<Course> newCourses) {
        courses.clear();
        if (newCourses != null) {
            courses.addAll(newCourses);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_course, parent, false);
        return new AttendanceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        Course course = courses.get(position);
        holder.tvCourseTitle.setText(course.getName());
        holder.tvTeacher.setText("استاد: " + course.getTeacherName());

        int present = course.getPresentCount();
        int absent = course.getAbsentCount();
        String summary = PersianDateHelper.toPersianDigits(present + " حاضر / " + absent + " غایب");
        holder.tvSummaryBadge.setText(summary);

        boolean isExpanded = expandedCourseIds.contains(course.getTcInfoId());
        holder.layoutContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.viewDivider.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.ivChevron.setImageResource(isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);

        holder.layoutHeader.setOnClickListener(v -> {
            if (expandedCourseIds.contains(course.getTcInfoId())) {
                expandedCourseIds.remove(course.getTcInfoId());
            } else {
                expandedCourseIds.add(course.getTcInfoId());
            }
            notifyItemChanged(holder.getAdapterPosition());
        });

        holder.layoutSessionsList.removeAllViews();
        List<AttendanceItem> sessions = course.getAttendanceList();

        if (sessions == null || sessions.isEmpty()) {
            holder.tvNoSessions.setVisibility(View.VISIBLE);
        } else {
            holder.tvNoSessions.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(context);

            for (AttendanceItem item : sessions) {
                View sessionView = inflater.inflate(R.layout.item_attendance_session, holder.layoutSessionsList, false);

                TextView tvDayTime = sessionView.findViewById(R.id.tv_session_day_time);
                TextView tvDate = sessionView.findViewById(R.id.tv_session_date);
                TextView tvStatus = sessionView.findViewById(R.id.tv_session_status);

                String dayTimeText = item.getDayName();
                if (!item.getStartTime().isEmpty()) {
                    dayTimeText += " - ساعت " + PersianDateHelper.toPersianDigits(item.getStartTime());
                }
                tvDayTime.setText(dayTimeText.isEmpty() ? "جلسه کلاسی" : dayTimeText);

                String dateText = item.getPersianDate();
                if (dateText.isEmpty()) {
                    dateText = item.getInsertDate();
                }
                tvDate.setText(PersianDateHelper.toPersianDigits(dateText));

                if (item.isPresent()) {
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_present);
                    tvStatus.setTextColor(0xFF10B981);
                    tvStatus.setText("✔️ حاضر");
                } else {
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_absent);
                    tvStatus.setTextColor(0xFFEF4444);
                    tvStatus.setText("❌ غایب");
                }

                holder.layoutSessionsList.addView(sessionView);
            }
        }
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    static class AttendanceViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutHeader, layoutContainer, layoutSessionsList;
        TextView tvCourseTitle, tvTeacher, tvSummaryBadge, tvNoSessions;
        ImageView ivChevron;
        View viewDivider;

        AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutHeader = itemView.findViewById(R.id.layout_attendance_header);
            layoutContainer = itemView.findViewById(R.id.layout_attendance_sessions_container);
            layoutSessionsList = itemView.findViewById(R.id.layout_attendance_sessions_list);
            tvCourseTitle = itemView.findViewById(R.id.tv_attendance_course_title);
            tvTeacher = itemView.findViewById(R.id.tv_attendance_teacher);
            tvSummaryBadge = itemView.findViewById(R.id.tv_attendance_summary_badge);
            tvNoSessions = itemView.findViewById(R.id.tv_no_attendance_sessions);
            ivChevron = itemView.findViewById(R.id.iv_attendance_chevron);
            viewDivider = itemView.findViewById(R.id.view_attendance_divider);
        }
    }
}
