package ir.ac.uk.elearn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CourseAccordionAdapter extends RecyclerView.Adapter<CourseAccordionAdapter.ViewHolder> {

    public interface OnFileDownloadListener {
        void onDownloadFile(Course course, FileItem file);
    }

    private final List<Course> courses = new ArrayList<>();
    private final Set<String> expandedCourseIds = new HashSet<>();
    private final OnFileDownloadListener listener;

    public CourseAccordionAdapter(OnFileDownloadListener listener) {
        this.listener = listener;
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_course_accordion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Course course = courses.get(position);
        holder.tvCourseTitle.setText(course.getName());

        String unitTypeStr = (course.getUnitType() == 1) ? "تئوری" : (course.getUnitType() == 2 ? "عملی" : "");
        String sub = "استاد: " + course.getTeacherName();
        if (!course.getGroupNo().isEmpty()) {
            sub += " (گروه " + PersianDateHelper.toPersianDigits(course.getGroupNo()) + (!unitTypeStr.isEmpty() ? " - " + unitTypeStr : "") + ")";
        }
        holder.tvCourseTeacher.setText(sub);

        int fileCount = (course.getLearningFiles() != null) ? course.getLearningFiles().size() : 0;
        holder.tvFilesCountBadge.setText(PersianDateHelper.toPersianDigits(String.valueOf(fileCount)) + " فایل");

        boolean isExpanded = expandedCourseIds.contains(course.getTcInfoId());
        holder.layoutFilesContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
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

        // Populate files inside accordion if expanded
        holder.layoutFileItemsList.removeAllViews();
        List<FileItem> files = course.getLearningFiles();
        if (files == null || files.isEmpty()) {
            holder.tvNoFiles.setVisibility(View.VISIBLE);
        } else {
            holder.tvNoFiles.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            for (FileItem file : files) {
                View fileRow = inflater.inflate(R.layout.item_file, holder.layoutFileItemsList, false);
                TextView tvTitle = fileRow.findViewById(R.id.tv_file_title);
                TextView tvDate = fileRow.findViewById(R.id.tv_file_date);
                MaterialButton btnDownload = fileRow.findViewById(R.id.btn_download_file);

                tvTitle.setText(file.getTitle());
                tvDate.setText("تاریخ بارگذاری: " + file.getPersianDate());

                btnDownload.setOnClickListener(v -> {
                    if (listener != null) listener.onDownloadFile(course, file);
                });

                holder.layoutFileItemsList.addView(fileRow);
            }
        }
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutHeader;
        ImageView ivChevron;
        TextView tvCourseTitle;
        TextView tvCourseTeacher;
        TextView tvFilesCountBadge;
        View viewDivider;
        LinearLayout layoutFilesContainer;
        TextView tvNoFiles;
        LinearLayout layoutFileItemsList;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutHeader = itemView.findViewById(R.id.layout_course_header);
            ivChevron = itemView.findViewById(R.id.iv_chevron);
            tvCourseTitle = itemView.findViewById(R.id.tv_course_title);
            tvCourseTeacher = itemView.findViewById(R.id.tv_course_teacher);
            tvFilesCountBadge = itemView.findViewById(R.id.tv_files_count_badge);
            viewDivider = itemView.findViewById(R.id.view_course_divider);
            layoutFilesContainer = itemView.findViewById(R.id.layout_files_container);
            tvNoFiles = itemView.findViewById(R.id.tv_no_files);
            layoutFileItemsList = itemView.findViewById(R.id.layout_file_items_list);
        }
    }
}
