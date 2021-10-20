package com.example.blazedocumentreader.fileviewer;

import static android.os.Build.VERSION.SDK_INT;

import static com.example.blazedocumentreader.fileviewer.Util.getSDCard;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.gridlayout.widget.GridLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.blazedocumentreader.DBManager;
import com.example.blazedocumentreader.R;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileViewerActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        SearchView.OnQueryTextListener {
    private static boolean mBusy = false, recentsView = false, favouritesView = false, homeView = true;
    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1, SDCARD_WRITE_PERMISSION_REQUEST_CODE = 100;
    private Toolbar toolbar;
    private DrawerLayout drawer;
    private RecyclerView lv;
    private GridLayout homeViewLayout;
    private File file, files[], origFiles[];
    private RecentFilesStack recent;
    private ArrayList<File> favourites;
    private EfficientAdapter adap;
    private FileFilter fileFilter;
    private Intent in;
    private TextView current_duration, total_duration, title;
    private ImageButton btn_play, btn_rev, btn_forward;
    private SeekBar seek;
    private static final String tempPath = Environment.getExternalStorageDirectory().getPath() + "/Blaze/temp/";
    private byte data[];
    private int sortCriterion = 0;
    private boolean isValid, sortDesc = false, listFoldersFirst = true, storeRecentItems = true, showHiddenFiles = true;
    private static final String[] requiredpermissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            Manifest.permission.INTERNET,
//            Manifest.permission.ACCESS_NETWORK_STATE,
    };
    //Comparators for sorting
    private Comparator<File> byName = new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(f1.getName(), f2.getName());
            return (res == 0 ? f1.getName().compareTo(f2.getName()) : res);
        }
    };
    private Comparator<File> byDate = new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            if (f1.lastModified() > f2.lastModified()) return 1;
            else if (f1.lastModified() < f2.lastModified()) return -1;
            else return 0;
        }
    };
    private Comparator<File> byDateDesc = new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            if (f1.lastModified() > f2.lastModified()) return -1;
            else if (f1.lastModified() < f2.lastModified()) return 1;
            else return 0;
        }
    };
    private Comparator<File> bySize = new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            if (f1.length() > f2.length()) return 1;
            else if (f1.length() < f2.length()) return -1;
            else return 0;
        }
    };
    private Comparator<File> bySizeDesc = new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            if (f1.length() > f2.length()) return -1;
            else if (f1.length() < f2.length()) return 1;
            else return 0;
        }
    };

    DBManager dbManager;
    SharedPreferences preferences;
    int flag = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_file_viewer);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        lv = findViewById(R.id.list);
        lv.setLayoutManager(new LinearLayoutManager(this));
        registerForContextMenu(lv);

        getPrefs();
        dbManager = new DBManager(this);
        dbManager.open();
        try {
            if (flag == 0) {
                dbManager.copyDataBase();
                flag = 1;
                editPrefs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        homeViewLayout = findViewById(R.id.home_view);
        homeViewLayout.findViewById(R.id.btn_document_files).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listMediaFiles(4);
            }
        });
        homeViewLayout.findViewById(R.id.btn_text_files).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listMediaFiles(6);
            }
        });
        homeViewLayout.findViewById(R.id.btn_document_files_pdf).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listMediaFiles(8);
            }
        });
        homeViewLayout.findViewById(R.id.btn_document_files_doc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listMediaFiles(9);
            }
        });
        homeViewLayout.findViewById(R.id.btn_document_files_xls).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listMediaFiles(10);
            }
        });
        homeViewLayout.findViewById(R.id.btn_document_files_ppt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listMediaFiles(11);
            }
        });
        homeViewLayout.findViewById(R.id.btn_favourites_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                favouriteFiles();
            }
        });
        homeViewLayout.findViewById(R.id.btn_recents_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recentFiles();
            }
        });
        //Restore data
        recent = new RecentFilesStack(10);
        favourites = new ArrayList<>();
        restoreData();
        ArrayList<String> storagePaths = new ArrayList<>();
        storagePaths.add(Environment.getExternalStorageDirectory().getPath());
        adap = new EfficientAdapter(getApplicationContext());
        updateFiles(Environment.getExternalStorageDirectory());
        lv.setAdapter(adap);
        //lv.setOnScrollListener(this);
        findViewById(R.id.btn_recents_view).setVisibility(storeRecentItems ? View.VISIBLE : View.GONE);

        for (int i = 0; i < storagePaths.size(); i++) {
            String path = storagePaths.get(i);
            final File f = new File(path);
            if (f.exists()) {
                RelativeLayout storageView = (RelativeLayout) getLayoutInflater().inflate(R.layout.storage_view, null, false);
                TextView details = storageView.findViewById(R.id.storage_details);
                TextView name = storageView.findViewById(R.id.storage_name);
                ProgressBar storageBar = storageView.findViewById(R.id.storage_bar);
                Long totalMemory = Util.getTotalMemoryInBytes(path), freeMemory = Util.getAvailableMemoryInBytes(path);
                float usedMemory = totalMemory - freeMemory;
                int percent = (int) ((usedMemory / totalMemory) * 100);
                storageBar.setProgress(percent);
                String memoryDetails = Util.displaySize(freeMemory) + " free out of " + Util.displaySize(totalMemory);
                details.setText(memoryDetails);
                if (path.equals(Environment.getExternalStorageDirectory().getPath())) {
                    name.setText("Internal Storage");
                } else {
                    name.setText("External Storage");
                }
                storageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        updateFiles(f);
                    }
                });
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.columnSpec = GridLayout.spec(0, 2);
                homeViewLayout.addView(storageView, i, params);
            }
        }

        in = getIntent();
        if (Intent.ACTION_VIEW.equals(in.getAction()) && in.getType() != null) {
            Uri uri = in.getData();
            if (uri != null) {
                openFile(new File(uri.getPath()));
            }
        } else {
            switchToHomeView();
        }
    }

    @Override
    protected void onStop() {
        saveData();
        freeMemory(true);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        onStop();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mainmenu, menu);
        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setOnQueryTextListener(this);
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (recentsView)
                    recentFiles();
                else if (favouritesView)
                    favouriteFiles();
                else if (homeView)
                    switchToHomeView();
                else if (file != null)
                    updateFiles(file);
                else
                    //Todo: something here
                    ;
                break;
            case R.id.action_info:
                if (file != null)
                    showProperties(file);
                else
                    //Todo: something here
                    ;
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        adap.getFilter().filter(query);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            switchToHomeView();
        } else if (id == R.id.nav_root) {
            File f = getRootDirectory();
            updateFiles(f);
        } else if (id == R.id.nav_sdcard) {
            String sdcard = getSDCard();
            if (sdcard != null) {
                File f = new File(sdcard);
                if (f.exists())
                    updateFiles(f);
                else
                    showMsg("External Storage not accessible", 1);
            } else
                showMsg("External Storage not accessible", 1);
        } else if (id == R.id.nav_camera) {
            File f = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            if (f == null || "".equals(f.getPath()) || !f.exists())
                f = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM");
            if (f.exists())
                updateFiles(f);
            else
                showMsg("Camera folder not accessible", 1);
        } else if (id == R.id.nav_downloads) {
            File f = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (f == null || "".equals(f.getPath()) || !f.exists())
                f = new File(Environment.getExternalStorageDirectory().getPath() + "/Downloads");
            if (f.exists())
                updateFiles(f);
            else
                showMsg("Downloads folder not accessible", 1);
        } else if (id == R.id.nav_favourites) {
            favouriteFiles();
        } else if (id == R.id.nav_recent) {
            recentFiles();
        } else if (id == R.id.nav_settings) {
            View settings_view = getLayoutInflater().inflate(R.layout.settings_view, null);
            AlertDialog.Builder settings_dialog = new AlertDialog.Builder(FileViewerActivity.this);
            settings_dialog.setIcon(android.R.drawable.ic_menu_preferences);
            settings_dialog.setTitle("Settings");
            settings_dialog.setView(settings_view);
            final CheckBox folders_first_checkbox = settings_view.findViewById(R.id.folders_first_checkbox);
            final CheckBox hidden_files_checkbox = settings_view.findViewById(R.id.hidden_files_checkbox);
            final CheckBox recent_items_checkbox = settings_view.findViewById(R.id.recent_items_checkbox);
            final Spinner sort_criteria = settings_view.findViewById(R.id.sort_criteria);
            final Spinner sort_mode = settings_view.findViewById(R.id.sort_mode);
            folders_first_checkbox.setChecked(listFoldersFirst);
            hidden_files_checkbox.setChecked(showHiddenFiles);
            recent_items_checkbox.setChecked(storeRecentItems);
            sort_criteria.setSelection(sortCriterion);
            sort_mode.setSelection(sortDesc ? 1 : 0);
            settings_view.findViewById(R.id.btn_clear_fav).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    favourites.clear();
                }
            });
            settings_view.findViewById(R.id.btn_clear_recent).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    recent.clear();
                }
            });
            settings_dialog.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    listFoldersFirst = folders_first_checkbox.isChecked();
                    showHiddenFiles = hidden_files_checkbox.isChecked();
                    storeRecentItems = recent_items_checkbox.isChecked();
                    String sortBy = sort_criteria.getSelectedItem().toString();
                    sortDesc = sort_mode.getSelectedItem().toString().equals("Descending");
                    String criteria[] = getResources().getStringArray(R.array.sort_criteria);
                    for (i = 0; i < criteria.length; i++) {
                        if (sortBy.equals(criteria[i])) {
                            sortCriterion = i;
                            break;
                        }
                    }
                    NavigationView nav = findViewById(R.id.nav_view);
                    nav.getMenu().findItem(R.id.nav_recent).setVisible(storeRecentItems);
                    if (recentsView) {
                        if (storeRecentItems)
                            recentFiles();
                        else {
                            recentsView = false;
                            updateFiles(Environment.getExternalStorageDirectory());
                        }
                    } else if (favouritesView)
                        favouriteFiles();
                    else
                        updateFiles(file);
                    showMsg("Settings saved", 1);
                    dialogInterface.dismiss();
                    dialogInterface.cancel();
                }
            });
            settings_dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    dialogInterface.cancel();
                }
            });
            settings_dialog.show();
        }
        drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (file != null && file.getParentFile() != null && updateFiles(file.getParentFile())) {
            return;
        } else if (!homeView) {
            switchToHomeView();
        } else super.onBackPressed();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.list) {
            menu.setHeaderIcon(android.R.drawable.ic_menu_manage);
            menu.setHeaderTitle("Actions");
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            int position = info.position;
            File current_file = files[position];
            if (current_file.isDirectory()) {
                menu.add(Menu.NONE, 1, Menu.NONE, "Open");
            } else {
                menu.add(Menu.NONE, 1, Menu.NONE, "Open with default system action");
                String ext = Util.extension(current_file.getName());
                if (Util.web_ext.contains(ext))
                    menu.add(Menu.NONE, 2, Menu.NONE, "Preview");
                menu.add(Menu.NONE, 3, Menu.NONE, "Share");
            }
            if (recentsView) {
                menu.add(Menu.NONE, 4, Menu.NONE, "Remove from Recent Items");
            } else if (favouritesView) {
                menu.add(Menu.NONE, 4, Menu.NONE, "Open parent directory");
            } else {
                menu.add(Menu.NONE, 4, Menu.NONE, "Delete");
            }
            menu.add(Menu.NONE, 5, Menu.NONE, favouritesView ? "Remove from Favourites" : "Add to Favourites");
            menu.add(Menu.NONE, 6, Menu.NONE, "Properties");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int position = menuInfo.position;
        final File current_file = files[position];
        switch (item.getItemId()) {
            case 1:
                if (current_file.isDirectory()) {
                    updateFiles(current_file);
                } else {
                    MimeTypeMap myMime = MimeTypeMap.getSingleton();
                    Intent in = new Intent(Intent.ACTION_VIEW);
                    String mimeType = myMime.getMimeTypeFromExtension(Util.extension(current_file.getName()));
                    Uri uri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName(), current_file);
                    in.setDataAndType(uri, mimeType);
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(in);
                    } catch (ActivityNotFoundException e) {
                        showMsg("No handler for this type of file.", 1);
                    }
                }
                return true;
            case 2:
//                Intent in = new Intent(FileViewerActivity.this, HTMLViewer.class);
//                in.putExtra("file", current_file.getPath());
//                startActivity(in);
//                return true;
            case 3:
                Intent share = new Intent();
                share.setAction(Intent.ACTION_SEND);
                MimeTypeMap myMime = MimeTypeMap.getSingleton();
                String mimeType = myMime.getMimeTypeFromExtension(Util.extension(current_file.getName()));
                share.setType(mimeType);
                share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(current_file));
                try {
                    startActivity(share);
                } catch (Exception e) {
                    e.printStackTrace();
                    showMsg("No handler available to share", 1);
                }
                return true;
            case 4:
                if (favouritesView) {
                    File f = current_file.getParentFile();
                    if (f != null && f.exists()) {
                        updateFiles(f);
                    } else {
                        showMsg("Parent directory can not be opened", 1);
                    }
                } else if (recentsView) {
                    int i = recent.indexOf(current_file);
                    if (i >= 0) {
                        recent.remove(i);
                        recentFiles();
                        showMsg(current_file.getName() + " removed from Recent Items", 1);
                    }
                } else {
                    AlertDialog.Builder confirmation_dialog = new AlertDialog.Builder(FileViewerActivity.this);
                    confirmation_dialog.setIcon(android.R.drawable.ic_delete);
                    confirmation_dialog.setTitle("Delete");
                    confirmation_dialog.setMessage("Are you sure you want to delete " + current_file.getName() + "?");
                    confirmation_dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int btn) {
                            if (deleteFiles(current_file)) {
                                showMsg(current_file.getName() + " successfully deleted", 1);
                                updateFiles(current_file.getParentFile());
                                //update recents and favorites
                                int i, n = favourites.size();
                                File arr[] = new File[n];
                                favourites.toArray(arr);
                                favourites.clear();
                                for (i = 0; i < n; i++) {
                                    File f = arr[i];
                                    if (f.exists())
                                        favourites.add(f);
                                }
                                RecentFilesStack temp = (RecentFilesStack<File>) recent.clone();
                                n = temp.size();
                                recent.clear();
                                for (i = 0; i < n; i++) {
                                    File f = (File) temp.get(i);
                                    if (f.exists())
                                        recent.push(f);
                                }
                            } else {
                                showMsg(current_file.getName() + " could not be deleted", 1);
                            }
                            dialogInterface.dismiss();
                            dialogInterface.cancel();
                        }
                    });
                    confirmation_dialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            dialogInterface.cancel();
                        }
                    });
                    confirmation_dialog.show();
                }
                return true;
            case 5:
                if (favouritesView) {
                    int i = favourites.indexOf(current_file);
                    if (i >= 0) {
                        favourites.remove(i);
                        favouriteFiles();
                        showMsg(current_file.getName() + " removed from Favourites", 1);
                    }
                } else {
                    if (favourites.size() < 20) {
                        favourites.add(current_file);
                        showMsg(current_file.getName() + " added to Favourites", 1);
                    } else
                        showMsg("Favourites list is full", 1);
                }
                return true;
            case 6:
                showProperties(current_file);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

//    @Override
//    public void onScroll(AbsListView absListView, int i, int i1, int i2) {
//
//    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == SDCARD_WRITE_PERMISSION_REQUEST_CODE && SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Uri treeUri = resultData.getData();
            if (treeUri != null) {
                grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        }
    }

    private void openFile(File current_file) {
        if (!current_file.exists())
            return;
        current_file.setReadable(true);
        if (!current_file.canRead()) {
            showMsg((current_file.isDirectory() ? "Folder" : "File") + " is not readable", 1);
            return;
        }
        recent.push(current_file);
        freeMemory(false);
        final MediaPlayer mp = new MediaPlayer();
        final MediaMetadataRetriever meta = new MediaMetadataRetriever();
        if (current_file.isDirectory()) {
            updateFiles(current_file);
        } else if (current_file.isFile()) {
            isValid = true;
            String ext = Util.extension(current_file.getName());
            if ("pdf".equals(ext)) {
//                in = new Intent(FileViewerActivity.this, DOCViewer.class);
//                in.putExtra("file", current_file.getPath());
//                in.putExtra("isPDF", true);
//                startActivity(in);
            } else {
                MimeTypeMap myMime = MimeTypeMap.getSingleton();
                Intent in = new Intent(Intent.ACTION_VIEW);
                String mimeType = myMime.getMimeTypeFromExtension(Util.extension(current_file.getName()));
                try {
                    Uri uri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName(), current_file);
                    in.setDataAndType(uri, mimeType);
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(in);
                } catch (ActivityNotFoundException e) {
                    showMsg("No handler for this type of file.", 1);
                } catch (Exception e) {
                    showMsg("Cannot open this file.", 1);
                }
            }
        }
    }

    private void showProperties(final File current_file) {
        MediaPlayer mp = new MediaPlayer();
        MediaMetadataRetriever meta = new MediaMetadataRetriever();
        String info = "", bitrate = "";
        int duration;
        AlertDialog.Builder properties_dialog = new AlertDialog.Builder(FileViewerActivity.this);
        View properties_view = getLayoutInflater().inflate(R.layout.properties_view, null);
        TextView name = properties_view.findViewById(R.id.name);
        TextView type = properties_view.findViewById(R.id.type);
        TextView time = properties_view.findViewById(R.id.time);
        final TextView size = properties_view.findViewById(R.id.size);
        final TextView details = properties_view.findViewById(R.id.details);
        //Fetch properties
        name.setText(current_file.getName());
        SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a");
        time.setText(format.format(current_file.lastModified()));
        properties_dialog.setTitle("Properties");
        properties_dialog.setView(properties_view);
        String file_info = getFilePermissions(current_file);
        if (file_info.length() > 29) {
            info += "Permissions : " + file_info.substring(1, 10);
            info += "\nOwner : " + file_info.substring(10, 19);
            info += "\nGroup : " + file_info.substring(19, 29);
        }
        info += "\nReadable : " + (current_file.canRead() ? "YES" : "NO");
        info += "\nHidden : " + (current_file.isHidden() ? "YES" : "NO");
        if (current_file.isFile()) {
            isValid = true;
            MimeTypeMap myMime = MimeTypeMap.getSingleton();
            String mimeType = myMime.getMimeTypeFromExtension(Util.extension(current_file.getName()));
            mimeType = ((mimeType == null || mimeType.equals("")) ? "Unknown" : mimeType);
            type.setText(mimeType);
            size.setText(Util.displaySize(current_file.length()));
            String ext = Util.extension(current_file.getName());
            if ("apk".equals(ext)) {
                String path = current_file.getPath();
                PackageManager pm = getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(path, 0);
                pi.applicationInfo.sourceDir = path;
                pi.applicationInfo.publicSourceDir = path;
                properties_dialog.setIcon(R.drawable.file_apk);
                info += "\nPackage Name : " + pi.packageName;
                info += "\nVersion Name : " + pi.versionName;
                info += "\nVersion Code : " + pi.versionCode;
                info += "\nApp Installed : " + (appInstalled(pi.packageName) ? "Yes" : "No");
            } else if (Util.audio_ext.contains(ext)) {
                properties_dialog.setIcon(R.drawable.file_music);
                try {
                    mp.setDataSource(current_file.getPath());
                    mp.prepare();
                    meta.setDataSource(current_file.getPath());
                } catch (Exception e) {
                    isValid = false;
                    e.printStackTrace();
                }
                if (isValid) {
                    duration = mp.getDuration();
                    mp.reset();
                    mp.release();
                    String album = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    album = ((album == null || "".equals(album)) ? "Unknown" : album);
                    String artist = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    artist = ((artist == null || "".equals(artist)) ? "Unknown" : artist);
                    String genre = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                    genre = ((genre == null || "".equals(genre)) ? "Unknown" : genre);
                    String year = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
                    year = ((year == null || "".equals(year)) ? "Unknown" : year);
                    bitrate = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                    bitrate = ((bitrate == null || "".equals(bitrate)) ? "Unknown" : bitrate);
                    meta.release();
                    info += "\nTrack Duration : " + Util.getFormattedTimeDuration(duration);
                    info += "\nAlbum : " + album;
                    info += "\nArtist : " + artist;
                    info += "\nGenre : " + genre;
                    info += "\nYear : " + year;
                    info += "\nBitrate : " + bitrate;
                } else {
                    info += "\nInvalid File";
                }
            } else if (Util.image_ext.contains(ext)) {
                properties_dialog.setIcon(new BitmapDrawable(getResources(), ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(current_file.getPath()), 50, 50)));
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                //Returns null, sizes are in the options variable
                BitmapFactory.decodeFile(current_file.getPath(), options);
                info += "\nWidth : " + options.outWidth + " pixels";
                info += "\nHeight : " + options.outHeight + " pixels";
            } else if (Util.video_ext.contains(ext)) {
                properties_dialog.setIcon(new BitmapDrawable(getResources(), ThumbnailUtils.createVideoThumbnail(current_file.getPath(), MediaStore.Images.Thumbnails.MINI_KIND)));
                try {
                    mp.setDataSource(current_file.getPath());
                    mp.prepare();
                    meta.setDataSource(current_file.getPath());
                } catch (Exception e) {
                    isValid = false;
                    e.printStackTrace();
                }
                if (isValid) {
                    duration = mp.getDuration();
                    mp.reset();
                    mp.release();
                    bitrate = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                    bitrate = ((bitrate == null || "".equals(bitrate)) ? "Unknown" : bitrate);
                    String frame_rate = "";
                    if (SDK_INT >= Build.VERSION_CODES.M) {
                        frame_rate = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                    }
                    frame_rate = ((frame_rate == null || "".equals(frame_rate)) ? "Unknown" : frame_rate);
                    String height = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    height = ((height == null || "".equals(height)) ? "Unknown" : height);
                    String width = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    width = ((width == null || "".equals(width)) ? "Unknown" : width);
                    meta.release();
                    info += "\nTrack Duration : " + Util.getFormattedTimeDuration(duration);
                    info += "\nBitrate : " + bitrate;
                    info += "\nWidth : " + width;
                    info += "\nHeight : " + height;
                    info += "\nFrame Rate : " + frame_rate;
                } else {
                    info += "\nInvalid File";
                }
            } else {
                properties_dialog.setIcon(R.drawable.file_default);
            }
            details.setText(info + "\nMD5 Checksum : calculating...");
            final Handler h = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    Bundle data = msg.getData();
                    String s = details.getText().toString();
                    s = s.substring(0, s.length() - 14);
                    details.setText(s + data.getString("MD5"));
                }
            };
            Thread t = new Thread() {
                @Override
                public void run() {
                    Message msg = new Message();
                    Bundle data = new Bundle();
                    data.putString("MD5", Util.md5(current_file.getPath()));
                    msg.setData(data);
                    msg.setTarget(h);
                    h.sendMessage(msg);
                }
            };
            t.start();
        } else if (current_file.isDirectory()) {
            type.setText("Directory");
            size.setText("calculating...");
            if (current_file.listFiles() != null) {
                int n = current_file.listFiles().length;
                properties_dialog.setIcon(n > 0 ? R.drawable.folder : R.drawable.folder_empty);
                info += "\n" + (n > 0 ? "Contains " + n + " Items" : "Empty Folder");
            }
            details.setText(info);
            final Handler h = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    Bundle data = msg.getData();
                    size.setText(data.getString("folder_size"));
                }
            };
            Thread t = new Thread() {
                @Override
                public void run() {
                    Message msg = new Message();
                    Bundle data = new Bundle();
                    data.putString("folder_size", Util.displaySize(getFolderSize(current_file)));
                    msg.setData(data);
                    msg.setTarget(h);
                    h.sendMessage(msg);
                }
            };
            t.start();
        }
        properties_dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                dialogInterface.cancel();
            }
        });
        properties_dialog.show();
    }

    private boolean updateFiles(File f) {
        if (f != null && f.exists() && f.listFiles() != null) {
            f.setReadable(true);
            f.setWritable(true);
            file = f;
            files = f.listFiles();
            files = sortFiles(files);
            origFiles = files.clone();
            toolbar.setTitle(file.getName());
            recentsView = false;
            favouritesView = false;
            updateList();
            return true;
        }
        return false;
    }

    private static boolean deleteFiles(File f) {
        if (f == null || !f.exists())
            return false;
        f.setWritable(true);
        if (f.isDirectory()) {
            File arr[] = f.listFiles();
            int i, n = arr.length;
            boolean deleted = true;
            for (i = 0; i < n; i++) {
                deleted &= deleteFiles(arr[i]);
            }
            return deleted && f.delete();
        } else return f.delete();
    }

    private void recentFiles() {
        RecentFilesStack temp = (RecentFilesStack<File>) recent.clone();
        files = new File[temp.size()];
        for (int i = 0; temp.size() > 0; i++) {
            files[i] = (File) temp.pop();
        }
        origFiles = files.clone();
        toolbar.setTitle("Recent Items");
        recentsView = true;
        favouritesView = false;
        updateList();
    }

    private void favouriteFiles() {
        files = new File[favourites.size()];
        files = favourites.toArray(files);
        files = sortFiles(files);
        origFiles = files.clone();
        toolbar.setTitle("Favourites");
        favouritesView = true;
        recentsView = false;
        updateList();
    }

    private void updateList() {
        homeView = false;
        lv.setVisibility(View.VISIBLE);
        homeViewLayout.setVisibility(View.GONE);
        if (adap != null) {
            adap.notifyDataSetChanged();
            mBusy = false;
        }
        lv.smoothScrollToPosition(0);
    }

    //Utility functions
    private void restoreData() {
        SharedPreferences prefs = getSharedPreferences("Blaze_Settings", MODE_PRIVATE);
        listFoldersFirst = prefs.getBoolean("FoldersFirst", true);
        showHiddenFiles = prefs.getBoolean("ShowHidden", true);
        storeRecentItems = prefs.getBoolean("ShowRecents", true);
        sortDesc = prefs.getBoolean("SortDesc", false);
        sortCriterion = prefs.getInt("SortCriterion", 0);
        prefs = getSharedPreferences("Blaze_Recent_Items", MODE_PRIVATE);
        Collection<?> c = prefs.getAll().values();
        String paths[] = new String[c.size()];
        paths = c.toArray(paths);
        File f;
        recent.clear();
        for (int i = 0; i < paths.length; i++) {
            f = new File(paths[i]);
            if (f.exists())
                recent.push(f);
        }
        prefs = getSharedPreferences("Blaze_Favourites", MODE_PRIVATE);
        c = prefs.getAll().values();
        paths = new String[c.size()];
        paths = c.toArray(paths);
        favourites.clear();
        for (int i = 0; i < paths.length; i++) {
            f = new File(paths[i]);
            if (f.exists())
                favourites.add(f);
        }
    }

    private boolean saveData() {
        try {
            SharedPreferences.Editor recent_editor = getSharedPreferences("Blaze_Recent_Items", MODE_PRIVATE).edit();
            SharedPreferences.Editor favourites_editor = getSharedPreferences("Blaze_Favourites", MODE_PRIVATE).edit();
            SharedPreferences.Editor settings_editor = getSharedPreferences("Blaze_Settings", MODE_PRIVATE).edit();
            settings_editor.clear();
            settings_editor.commit();
            settings_editor.putBoolean("FoldersFirst", listFoldersFirst);
            settings_editor.putBoolean("ShowHidden", showHiddenFiles);
            settings_editor.putBoolean("ShowRecents", storeRecentItems);
            settings_editor.putBoolean("SortDesc", sortDesc);
            settings_editor.putInt("SortCriterion", sortCriterion);
            settings_editor.commit();
            recent_editor.clear();
            recent_editor.commit();
            File f;
            RecentFilesStack temp = (RecentFilesStack<File>) recent.clone();
            int i, n = temp.size();
            for (i = 0; i < n; i++) {
                f = (File) temp.pop();
                if (f.exists())
                    recent_editor.putString(f.getName(), f.getPath());
            }
            recent_editor.commit();
            favourites_editor.clear();
            favourites_editor.commit();
            n = favourites.size();
            File arr[] = new File[n];
            favourites.toArray(arr);
            for (i = 0; i < n; i++) {
                f = arr[i];
                if (f.exists())
                    favourites_editor.putString(f.getName(), f.getPath());
            }
            favourites_editor.commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private File[] sortFiles(File f[]) {
        int i, n;
        if (!showHiddenFiles) {
            ArrayList<File> temp = new ArrayList<File>();
            n = f.length;
            for (i = 0; i < n; i++) {
                if (!f[i].isHidden())
                    temp.add(f[i]);
            }
            f = new File[temp.size()];
            f = temp.toArray(f);
        }
        //Sort Alphabetically
        Arrays.sort(f, byName);
        if (sortCriterion == 1)
            Arrays.sort(f, sortDesc ? byDateDesc : byDate);
        else if (sortCriterion == 2)
            Arrays.sort(f, sortDesc ? bySizeDesc : bySize);
        else if (sortDesc) {
            File temp;
            n = f.length;
            for (i = 0; i < n / 2; i++) {
                temp = f[i];
                f[i] = f[n - i - 1];
                f[n - i - 1] = temp;
            }
        }
        if (listFoldersFirst) {
            try {
                Arrays.sort(f, new Comparator<File>() {
                    @Override
                    public int compare(File f1, File f2) {
                        if (f1.isDirectory() && !f2.isDirectory()) return -1;
                        else if (!f1.isDirectory() && f2.isDirectory()) return 1;
                        else return 0;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return f;
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                showMsg("Permissions not granted", 0);
                finish();
                return;
            }
        }
    }

    private List<String> getNeededPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : requiredpermissions) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(permission);
                }
            }
        }
        return listPermissionsNeeded;
    }

    private void checkAndRequestPermissions() {
        List<String> neededPermissions = getNeededPermissions();
        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[neededPermissions.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
        }
    }

    private static String unpackZip(File zipFile, File targetDirectory) {
        String dirName = "";
        try {
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
            ZipEntry ze;
            int count = -2;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                if (count == -2) {
                    dirName = ze.getName().split("/")[0];
                }
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    return "";
                }
                if (ze.isDirectory()) {
                    continue;
                }
                FileOutputStream fout = new FileOutputStream(file);
                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                }
                // restore time as well
                long time = ze.getTime();
                if (time > 0) {
                    file.setLastModified(time);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return dirName;
    }

    private static void freeMemory(boolean deleteTempFiles) {
        //remove temp files
        if (deleteTempFiles) {
            deleteFiles(new File(tempPath));
        }
        //try to free ram
        System.runFinalization();
        Runtime.getRuntime().gc();
        System.gc();
    }

    private void showMsg(String msg, int mode) {
        if (mode == 0)
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        else
            Snackbar.make(findViewById(R.id.list), msg, Snackbar.LENGTH_LONG).setAction("Action", null).show();
    }

    private static String getFilePermissions(File file) {
        String s = "";
        if (file.getParent() != null) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("ls", "-l").directory(new File(file.getParent()));// TODO CHECK IF THE FILE IS SD CARD PARENT IS NULL
                Process process = processBuilder.start();
                PrintWriter out = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));
                BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                out.flush();
                s = in.readLine();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (s == null) s = "";
        return s;
    }

    private static long getFolderSize(File file) {
        long size = 0;
        if (file != null && file.exists() && file.isDirectory()) {
            File arr[] = file.listFiles();
            if (arr != null) {
                for (File child : arr) {
                    if (child.isDirectory())
                        size += getFolderSize(child);
                    else size += child.length();
                }
            }
        }
        return size;
    }

    private boolean appInstalled(String uri) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static File getRootDirectory() {
        File f = Environment.getRootDirectory();
        f.setReadable(true);
        if (!f.canRead()) {
            return null;
        }
        while (true) {
            File parent = f.getParentFile();
            if (parent == null || !parent.exists()) {
                break;
            }
            parent.setReadable(true);
            if (!parent.canRead()) {
                break;
            }
            f = parent;
        }
        return f;
    }

    private void listMediaFiles(int type) {
        Uri uri = null;
        String toolbarTitle = "";
        String selectionQuery = null;
        String[] selectionArgs = null;
        switch (type) {
            case 1: //images
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                toolbarTitle = "Pictures";
                break;
            case 2: //audio
                uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                toolbarTitle = "Music";
                break;
            case 3: //video
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                toolbarTitle = "Videos";
                break;
            case 4: //documents
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = "Documents";
                List<String> extList = new ArrayList<>(Util.doc_ext);
                extList.addAll(Util.xl_ext);
                extList.addAll(Util.ppt_ext);
                extList.addAll(Util.opendoc_ext);
                extList.add("pdf");
                selectionArgs = Util.getMimeTypeQueryArgs(extList);
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);
                break;
            case 5: //archives
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = "Archives";
                selectionArgs = Util.getMimeTypeQueryArgs(Util.archive_ext);
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);
                break;
            case 6: //text files
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = "Text files";
                selectionArgs = Util.getMimeTypeQueryArgs(Util.txt_ext);
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);
                break;
            case 7: //apk files
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = "Apps";
                selectionArgs = Util.getMimeTypeQueryArgs(Arrays.asList("apk"));
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);

            case 8: //pdf documents
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = getResources().getString(R.string.pdf_documents);
                selectionArgs = Util.getMimeTypeQueryArgs(Arrays.asList("pdf"));
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);
                break;

            case 9: //word documents
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = getResources().getString(R.string.word_documents);
                List<String> extList1 = new ArrayList<>(Util.doc_ext);
                selectionArgs = Util.getMimeTypeQueryArgs(extList1);
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);
                break;

            case 10: //excel documents
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = getResources().getString(R.string.xls_documents);
                List<String> extList2 = new ArrayList<>(Util.xl_ext);
                selectionArgs = Util.getMimeTypeQueryArgs(extList2);
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);
                break;

            case 11: //powerpoint documents
                uri = MediaStore.Files.getContentUri("external");
                toolbarTitle = getResources().getString(R.string.powerpoint_documents);
                List<String> extList3 = new ArrayList<>(Util.ppt_ext);
                selectionArgs = Util.getMimeTypeQueryArgs(extList3);
                selectionQuery = Util.getMimeTypeQuery(selectionArgs);
                break;
        }
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(uri, null, selectionQuery, selectionArgs, null);
        ArrayList<File> fileList = new ArrayList<>();
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                File f = new File(data);
                if (f != null && f.exists()) {
                    fileList.add(f);
                }
            }
        }
        cursor.close();
        files = new File[fileList.size()];
        files = fileList.toArray(files);
        if (files != null) {
            file = null;
            files = sortFiles(files);
            origFiles = files.clone();
            toolbar.setTitle(toolbarTitle);
            recentsView = false;
            favouritesView = false;
            updateList();
        } else {
            files = origFiles;
            showMsg("No " + toolbarTitle + " found", 1);
        }
    }

    private void requestSDCardPermissions(String cardPath) {
        if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), SDCARD_WRITE_PERMISSION_REQUEST_CODE);
        }
    }

    private void switchToHomeView() {
        homeView = true;
        toolbar.setTitle("Blaze");
        homeViewLayout.setVisibility(View.VISIBLE);
        lv.setVisibility(View.GONE);
    }

    class EfficientAdapter extends RecyclerView.Adapter<EfficientAdapter.MyAdapter> implements Filterable {

        private LayoutInflater mInflater;
        private Context mContext;
        private LayoutInflater inflater;

        public EfficientAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContext = context;
        }

        @NonNull
        @Override
        public MyAdapter onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (inflater == null) {
                inflater = LayoutInflater.from(parent.getContext());
            }
            View view = inflater.inflate(R.layout.list_item, parent, false);
            return new MyAdapter(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MyAdapter holder, int position) {
            File current_file = files[position];
            holder.name.setText(current_file.getName());
            SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a");
            holder.date.setText(format.format(current_file.lastModified()));
            if (current_file.isFile()) {
                holder.details.setText(Util.displaySize(current_file.length()));
            } else if (current_file.isDirectory()) {
                File temp[] = current_file.listFiles();
                int n = (temp != null ? temp.length : 0);
                holder.details.setText(n + " items");
                holder.bookmark.setVisibility(View.GONE);
            } else {
                holder.details.setText("");
            }
            if (!mBusy) {
                if (current_file.isDirectory()) {
                    holder.icon.setImageResource(R.drawable.folder);
                } else if (current_file.isFile()) {
                    isValid = true;
                    String ext = Util.extension(current_file.getName());
                    if (Util.doc_ext.contains(ext)) {
                        holder.icon.setImageResource(R.drawable.word);
                    } else if (Util.xl_ext.contains(ext)) {
                        holder.icon.setImageResource(R.drawable.excel);
                    } else if (Util.ppt_ext.contains(ext)) {
                        holder.icon.setImageResource(R.drawable.powerpoint);
                    } else if ("pdf".equals(ext)) {
                        holder.icon.setImageResource(R.drawable.pdf);
                    }
                    else {
                        holder.icon.setImageResource(R.drawable.file_default);
                    }
                } else {
                    holder.icon.setImageResource(R.drawable.file_default);
                }
            } else {
                holder.icon.setImageResource(R.drawable.loading);
            }

            String type = "";
            String ext = Util.extension(files[position].getName());
            if (Util.doc_ext.contains(ext)) {
                type = "word";
            } else if (Util.xl_ext.contains(ext)) {
                type = "excel";
            } else if (Util.ppt_ext.contains(ext)) {
                type = "ppt";
            } else if ("pdf".equals(ext)) {
                type = "pdf";
            } else {
                type = "default";
            }
            if (dbManager.getFavourites(files[position].getName(), type).size()==0){
                holder.bookmark.setImageResource(R.drawable.ic_favorite_red);
            }
        }

        @Override
        public int getItemCount() {
            return files != null ? files.length : 0;
        }

        @Override
        public Filter getFilter() {
            if (fileFilter == null) {
                fileFilter = new FileFilter();
            }
            return fileFilter;
        }

        public class MyAdapter extends RecyclerView.ViewHolder {
            private TextView name;
            private TextView date;
            private TextView details;
            private ImageView icon;
            private ImageView bookmark;

            public MyAdapter(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.name);
                date = itemView.findViewById(R.id.date);
                details = itemView.findViewById(R.id.details);
                icon = itemView.findViewById(R.id.icon);
                bookmark = itemView.findViewById(R.id.bookmark);

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openFile(files[getAdapterPosition()]);
                    }
                });

                bookmark.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        String type = "";
                        String ext = Util.extension(files[getAdapterPosition()].getName());
                        if (Util.doc_ext.contains(ext)) {
                            type = "word";
                        } else if (Util.xl_ext.contains(ext)) {
                            type = "excel";
                        } else if (Util.ppt_ext.contains(ext)) {
                            type = "ppt";
                        } else if ("pdf".equals(ext)) {
                            type = "pdf";
                        } else {
                            type = "default";
                        }
                        if (dbManager.getFavourites(files[getAdapterPosition()].getName(), type).size()==0){
                            bookmark.setImageResource(R.drawable.ic_favorite_red);
                        }
                        String name = files[getAdapterPosition()].getName();

                        dbManager.addFav(type, name);
                        Toast.makeText(mContext, "sizeis: " + dbManager.getFavourites(name, type).size() + "", Toast.LENGTH_SHORT).show();

                    }
                });
            }
        }
    }

    class RecentFilesStack<File> extends Stack<File> {
        private int maxSize;

        public RecentFilesStack(int size) {
            super();
            this.maxSize = size;
        }

        @Override
        public File push(File f) {
            int i = this.indexOf(f);
            if (i >= 0)
                this.remove(i);
            while (this.size() >= maxSize) {
                this.remove(0);
            }
            return super.push(f);
        }
    }

    class FileFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults filterResults = new FilterResults();
            if (constraint != null && constraint.length() > 0) {
                ArrayList<File> tempList = new ArrayList<File>();
                for (File f : origFiles) {
                    if (f.getName().toLowerCase().contains(constraint.toString().toLowerCase())) {
                        tempList.add(f);
                    }
                }
                filterResults.count = tempList.size();
                File f[] = new File[filterResults.count];
                filterResults.values = tempList.toArray(f);
            } else {
                filterResults.count = files.length;
                filterResults.values = files;
            }
            return filterResults;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults results) {
            files = (File[]) results.values;
            updateList();
        }
    }

    public void editPrefs() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        preferences.getInt("flag", flag);
        editor.apply();
        editor.commit();
    }

    public void getPrefs() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        flag = preferences.getInt("flag", 0);
    }

}