package com.halftonestudio;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen project library ("My projects"), extracted from StudioActivity in build 554.
 * Search and sort run in memory; thumbnails and every project action go through {@link Host},
 * so this screen performs no file I/O itself.
 */
final class ProjectGalleryDialog {
    interface Host {
        boolean hasEditableProject();
        void requestNewProject();
        void saveCurrentProject();
        void importProjectZip();
        void chooseLibraryBackup(boolean export);
        void openProject(StudioProjectLibrary.Entry entry);
        void cardMenu(View anchor,StudioProjectLibrary.Entry entry,Dialog gallery);
        void bindThumbnail(String id,ImageView target);
        String searchQuery();
        void setSearchQuery(String query);
        android.content.SharedPreferences prefs();
    }
    private final Activity activity;
    private final StudioUi ui;
    private final Host host;
    ProjectGalleryDialog(Activity activity,StudioUi ui,Host host){this.activity=activity;this.ui=ui;this.host=host;}

    void show(final List<StudioProjectLibrary.Entry> saved){
        final Dialog gallery=new Dialog(activity);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(15),ui.dp(12),ui.dp(15),ui.dp(8));root.setBackgroundColor(StudioTheme.BG);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=35){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|
                    WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;
            }else{
                left=top=right=bottom=0; // non-edge-to-edge library dialog
            }
            root.setPadding(ui.dp(15)+left,ui.dp(12)+top,ui.dp(15)+right,ui.dp(8)+bottom);
            return insets;
        });
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption=ui.text(activity.getString(R.string.ui_my_projects),23,StudioTheme.TEXT,true);
        heading.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        TextView moreActions=ui.text("⋮",26,StudioTheme.TEXT,true);moreActions.setGravity(Gravity.CENTER);
        moreActions.setContentDescription(moreActions.getContext().getString(R.string.quality_library_backup_and_restore_options));ui.styleHeaderButton(moreActions);
        moreActions.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(activity,moreActions);
            menu.getMenu().add("Backup all projects");menu.getMenu().add("Restore backup ZIP");
            menu.setOnMenuItemClickListener(item->{
                String label=item.getTitle().toString();gallery.dismiss();
                host.chooseLibraryBackup(label.equals("Backup all projects"));return true;
            });menu.show();
        });
        heading.addView(moreActions,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        TextView close=ui.text("✕",22,StudioTheme.TEXT,true);close.setGravity(Gravity.CENTER);
        close.setContentDescription(close.getContext().getString(R.string.quality_close_project_library));ui.styleHeaderButton(close);
        close.setOnClickListener(v->gallery.dismiss());heading.addView(close,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        root.addView(heading);

        LinearLayout actions=new LinearLayout(activity);actions.setPadding(0,ui.dp(9),0,ui.dp(10));
        TextView newProject=ui.text("New Project",13,StudioTheme.TEXT,true);newProject.setGravity(Gravity.CENTER);
        newProject.setBackground(ui.pillBackground(true,14));
        newProject.setContentDescription(newProject.getContext().getString(R.string.quality_start_a_new_project_from_a_photograph));
        newProject.setOnClickListener(v->{gallery.dismiss();host.requestNewProject();});
        LinearLayout.LayoutParams firstAction=new LinearLayout.LayoutParams(0,ui.dp(48),1);firstAction.rightMargin=ui.dp(8);
        actions.addView(newProject,firstAction);
        TextView save=ui.text("Save Current",13,StudioTheme.TEXT,true);save.setGravity(Gravity.CENTER);
        save.setBackground(ui.pillBackground(false,14));
        save.setContentDescription(!host.hasEditableProject()?"No editable project to save":"Save current editable project");
        save.setEnabled(host.hasEditableProject());save.setAlpha(!host.hasEditableProject()?.45f:1f);
        save.setOnClickListener(v->{if(host.hasEditableProject()){gallery.dismiss();host.saveCurrentProject();}});
        LinearLayout.LayoutParams middleAction=new LinearLayout.LayoutParams(0,ui.dp(48),1);middleAction.rightMargin=ui.dp(8);
        actions.addView(save,middleAction);
        TextView importZip=ui.text("Import ZIP",13,StudioTheme.TEXT,true);importZip.setGravity(Gravity.CENTER);
        importZip.setBackground(ui.pillBackground(false,14));importZip.setOnClickListener(v->{gallery.dismiss();host.importProjectZip();});
        actions.addView(importZip,new LinearLayout.LayoutParams(0,ui.dp(48),1));root.addView(actions);

        EditText search=new EditText(activity);search.setSingleLine(true);
        search.setTextSize(15);search.setTextColor(StudioTheme.TEXT);search.setHintTextColor(StudioTheme.MUTED);
        search.setHint("Search projects or styles");search.setPadding(ui.dp(14),0,ui.dp(14),0);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(ui.outlined(StudioTheme.SURFACE_HIGH,StudioTheme.OUTLINE,12));
        search.setContentDescription(search.getContext().getString(R.string.quality_search_saved_projects_by_name_or_artwork_style));
        search.setText(host.searchQuery());
        root.addView(search,new LinearLayout.LayoutParams(-1,ui.dp(48)));

        LinearLayout indexRow=new LinearLayout(activity);indexRow.setGravity(Gravity.CENTER_VERTICAL);
        indexRow.setPadding(ui.dp(2),ui.dp(7),0,ui.dp(5));
        TextView count=ui.text("",12,StudioTheme.MUTED,false);indexRow.addView(count,new LinearLayout.LayoutParams(0,ui.dp(46),1));
        count.setGravity(Gravity.CENTER_VERTICAL);
        final int[] sorting={StudioProjectGalleryModel.normalizeSort(host.prefs().getInt("gallerySort",StudioProjectGalleryModel.RECENT))};
        TextView sorter=ui.text(StudioProjectGalleryModel.sortLabel(sorting[0])+" ▾",13,StudioTheme.TEXT,true);
        sorter.setGravity(Gravity.CENTER);sorter.setBackground(ui.pillBackground(false,12));
        sorter.setContentDescription("Sort projects. Current order: "+StudioProjectGalleryModel.sortLabel(sorting[0]));
        indexRow.addView(sorter,new LinearLayout.LayoutParams(ui.dp(116),ui.dp(48)));
        root.addView(indexRow);

        FrameLayout content=new FrameLayout(activity);
        final ArrayList<StudioProjectLibrary.Entry> visible=new ArrayList<>();
        GridView grid=new GridView(activity);
        grid.setNumColumns(StudioProjectGalleryModel.columnsForWidth(activity.getResources().getConfiguration().screenWidthDp));
        grid.setVerticalSpacing(ui.dp(12));grid.setHorizontalSpacing(ui.dp(12));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setClipToPadding(false);
        grid.setPadding(0,ui.dp(4),0,ui.dp(20));
        android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){
            @Override public int getCount(){return visible.size();}
            @Override public Object getItem(int position){return visible.get(position);}
            @Override public long getItemId(int position){return visible.get(position).id.hashCode();}
            @Override public boolean hasStableIds(){return true;}
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
                final StudioProjectLibrary.Entry entry=visible.get(position);
                LinearLayout cell=recycled instanceof LinearLayout?(LinearLayout)recycled:new LinearLayout(activity);
                cell.removeAllViews();cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(ui.dp(7),ui.dp(7),ui.dp(7),ui.dp(9));
                cell.setBackground(ui.ripple(ui.outlined(StudioTheme.SURFACE,StudioTheme.OUTLINE,14),14));
                ImageView picture=new ImageView(activity);
                picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
                picture.setBackground(ui.shape(0xff182029,9));picture.setTag(entry.id);
                cell.addView(picture,new LinearLayout.LayoutParams(-1,ui.dp(156)));
                host.bindThumbnail(entry.id,picture);
                LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
                TextView projectName=ui.text(entry.damaged?entry.name+" · Unreadable":entry.name,13,
                    entry.damaged?StudioTheme.MUTED:StudioTheme.TEXT,true);projectName.setMaxLines(1);
                projectName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(projectName,new LinearLayout.LayoutParams(0,ui.dp(37),1));
                TextView more=ui.text("⋮",22,StudioTheme.TEXT,true);more.setGravity(Gravity.CENTER);
                more.setContentDescription("Options for "+entry.name);ui.styleHeaderButton(more);
                more.setOnClickListener(v->host.cardMenu(more,entry,gallery));
                row.addView(more,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
                cell.addView(row);
                TextView detail=ui.text(entry.damaged?"Recovery or deletion required":
                    entry.style+" · "+new java.text.SimpleDateFormat("MMM d",Locale.US).format(new Date(entry.modified)),
                    11,StudioTheme.MUTED,false);
                detail.setMaxLines(1);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(detail);
                cell.setContentDescription(entry.damaged?"Unreadable project "+entry.name+", recovery options available":
                    "Open "+entry.name+", "+entry.style+", saved project");
                cell.setOnClickListener(v->{gallery.dismiss();host.openProject(entry);});
                cell.setFocusable(false);
                return cell;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent,view,position,id)->{
            if(position<0||position>=visible.size())return;
            StudioProjectLibrary.Entry selected=visible.get(position);
            gallery.dismiss();host.openProject(selected);
        });
        content.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=ui.text("",14,StudioTheme.MUTED,false);
        empty.setGravity(Gravity.CENTER);empty.setPadding(ui.dp(20),ui.dp(38),ui.dp(20),ui.dp(38));
        content.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));

        final Runnable refresh=()->{
            List<StudioProjectLibrary.Entry> filtered=StudioProjectGalleryModel.visible(saved,search.getText().toString(),sorting[0]);
            visible.clear();visible.addAll(filtered);
            adapter.notifyDataSetChanged();
            count.setText(filtered.size()==saved.size()?saved.size()+" saved project"+(saved.size()==1?"":"s"):
                filtered.size()+" of "+saved.size()+" projects");
            boolean noResults=filtered.isEmpty();
            grid.setVisibility(noResults?View.GONE:View.VISIBLE);
            empty.setVisibility(noResults?View.VISIBLE:View.GONE);
            empty.setText(saved.isEmpty()?"No saved projects yet. Start with a photo or import an existing project ZIP.":
                "No projects match your search. Try a different name or style.");
        };
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                host.setSearchQuery(s.toString());refresh.run();
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        });
        sorter.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(activity,sorter);
            for(int which=StudioProjectGalleryModel.RECENT;which<=StudioProjectGalleryModel.STYLE;which++){
                final int selected=which;
                menu.getMenu().add(StudioProjectGalleryModel.sortLabel(which))
                    .setCheckable(true).setChecked(sorting[0]==which)
                    .setOnMenuItemClickListener(item->{
                        sorting[0]=selected;
                        host.prefs().edit().putInt("gallerySort",selected).apply();
                        sorter.setText(activity.getString(R.string.gallery_sort_dropdown,StudioProjectGalleryModel.sortLabel(selected)));
                        sorter.setContentDescription("Sort projects. Current order: "+StudioProjectGalleryModel.sortLabel(selected));
                        refresh.run();grid.setSelection(0);return true;
                    });
            }
            menu.show();
        });
        refresh.run();
        TextView safety=ui.text(activity.getString(R.string.ui_back_up_your_projects_before_uninstalling),11,StudioTheme.MUTED,false);
        safety.setPadding(ui.dp(2),ui.dp(4),ui.dp(2),0);root.addView(safety);
        gallery.setContentView(root);gallery.show();
        if(gallery.getWindow()!=null){
            gallery.getWindow().setBackgroundDrawable(new ColorDrawable(StudioTheme.BG));
            gallery.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            gallery.getWindow().setLayout(-1,-1);
        }
        }
}
