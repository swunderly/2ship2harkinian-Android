package org.comboship.android;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
public final class MainActivity extends Activity {
    private static final int OOT=10, MM=11, MOD_OOT=12, MOD_MM=13, BACKUP=14;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Button> actions=new ArrayList<>();
    private TextView status; private File root; private boolean busy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density); body.setPadding(pad,pad,pad,pad);
        scroll.addView(body); setContentView(scroll);
        TextView title=new TextView(this); title.setText("ComboShip Android"); title.setTextSize(29); body.addView(title);
        TextView description=new TextView(this);
        description.setText("Ocarina of Time + Majora’s Mask\nExperimental native combined randomizer\n\nSeparate installation and saves from your existing 2S2H app.");
        description.setTextSize(16); body.addView(description);
        status=new TextView(this); status.setTextSize(15); status.setPadding(0,pad,0,pad); body.addView(status);
        add(body,"Import Ocarina of Time ROM",()->pick(OOT));
        add(body,"Import Majora’s Mask ROM",()->pick(MM));
        add(body,"Play / open combined randomizer",this::launch);
        add(body,"Import an OoT mod archive",()->pick(MOD_OOT));
        add(body,"Import an MM mod archive",()->pick(MOD_MM));
        add(body,"Export saves, settings, and logs",this::backup);
        add(body,"Verify / repair bundled support files",this::prepare);
        add(body,"Instructions and build status",()->new AlertDialog.Builder(this).setTitle("Development build")
            .setMessage("Import your own unmodified, supported N64 ROMs. Byte order is normalized during import; the game extractor checks version compatibility.\n\nUse ComboShip’s own seed generator. Web OoTMM seed compatibility is not assumed.\n\nThe in-game Menu button opens the native QoL, randomizer, graphics, audio, and controller settings. Hold Menu for touch-layout options.\n\nExport a backup before changing builds. This app never accesses or converts your existing standalone 2S2H saves. Backups exclude ROMs and mod archives.\n\nThis is a development port. Compilation does not certify gameplay or full Android QoL parity.")
            .setPositiveButton("Close",null).show());
        try { root=DataFiles.root(this); prepare(); } catch(IOException error) {showError(error);}
    }
    private void add(LinearLayout parent,String label,Runnable action) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false);
        button.setOnClickListener(v->action.run()); parent.addView(button); actions.add(button);
    }
    private void setBusy(boolean value,String text) {busy=value; status.setText(text); for(Button button:actions) button.setEnabled(!value);}
    private interface Work {void run() throws Exception;}
    private void execute(String text,Work work,String complete) {
        if(busy || root==null) return;
        setBusy(true,text);
        worker.execute(()->{
            Exception failure=null;
            try(DataLock ignored=new DataLock(root)){work.run();} catch(Exception error){failure=error;}
            final Exception result=failure;
            runOnUiThread(()->{
                if(isFinishing() || isDestroyed()) return;
                setBusy(false,complete); refresh();
                if(result!=null) showError(result); else Toast.makeText(this,complete,Toast.LENGTH_SHORT).show();
            });
        });
    }
    private void prepare(){execute("Verifying the bundled engine support files…",()->DataFiles.installSupport(this,root),"Support files verified.");}
    private void refresh() {
        if(root==null || busy) return;
        status.setText("OoT ROM: "+(new File(root,"oot.z64").isFile()?"imported":"needed")+
            "\nMM ROM: "+(new File(root,"mm.z64").isFile()?"imported":"needed")+"\nBuild: "+BuildConfig.VERSION_NAME);
        File error=new File(root,"last-startup-error.txt");
        if(error.isFile()) {
            try(InputStream in=new FileInputStream(error)) {status.append("\n\nLast launch: "+new String(DataFiles.readLimited(in,16384),java.nio.charset.StandardCharsets.UTF_8));}
            catch(IOException ignored) {status.append("\nLast launch failed. Export logs for details.");}
        }
    }
    private void pick(int request){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),request);}
    private void backup(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"ComboShip-backup-"+System.currentTimeMillis()+".zip"); startActivityForResult(intent,BACKUP);
    }
    private String displayName(Uri uri) {
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null && cursor.moveToFirst()) return cursor.getString(0);
        } catch(RuntimeException ignored) {} return "";
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        if(request==OOT || request==MM) {
            execute("Copying and validating the selected ROM…",()->{
                try(InputStream in=getContentResolver().openInputStream(uri)){RomImporter.importRom(in,new File(root,request==OOT?"oot.z64":"mm.z64"));}
            },"ROM imported. The engine will verify game/version compatibility.");
        } else if(request==MOD_OOT || request==MOD_MM) {
            String name=displayName(uri);
            execute("Importing the selected mod…",()->{
                try(InputStream in=getContentResolver().openInputStream(uri)){DataFiles.importMod(in,root,request==MOD_OOT?"soh":"2ship",name);}
            },"Mod imported into this app only.");
        } else if(request==BACKUP) {
            execute("Exporting a consistent backup…",()->{
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){DataFiles.exportBackup(root,out);}
            },"Backup exported without ROMs or mods.");
        }
    }
    private void launch(){
        if(root==null || busy) return;
        if(!new File(root,"oot.z64").isFile() || !new File(root,"mm.z64").isFile()){
            new AlertDialog.Builder(this).setMessage("Import both supported ROMs first.").setPositiveButton("OK",null).show(); return;
        }
        Intent game=new Intent(this,GameActivity.class); game.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(game);
    }
    private void showError(Exception error){
        String message=error.getMessage(); if(message==null) message=error.toString(); status.setText(message);
        new AlertDialog.Builder(this).setTitle("Action could not finish").setMessage(message).setPositiveButton("Close",null).show();
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
