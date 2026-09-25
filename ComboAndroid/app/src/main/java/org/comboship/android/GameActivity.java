package org.comboship.android;
import org.libsdl.app.SDLActivity;
import android.app.AlertDialog;
import android.os.*;
import android.view.*;
import java.io.File;
/** A dedicated process gives the combined native runtime a clean lifetime on every relaunch. */
public final class GameActivity extends SDLActivity {
    private TouchOverlay overlay; private boolean librariesReady;
    public static native void nativePad(int buttons,float lx,float ly,float rx,float ry,float lt,float rt);
    public static native void nativeMenu();
    public static native void nativeRelease();
    public static native void nativeQuit();
    public static native boolean nativeControlsSuppressed();
    public static native void nativeMenuScale(float scale);
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private final Runnable updateControls=new Runnable(){public void run(){
        if(librariesReady && overlay!=null)overlay.setSuppressed(nativeControlsSuppressed());
        uiHandler.postDelayed(this,100);
    }};
    @Override protected String[] getLibraries(){return new String[]{"SDL2","comboship"};}
    @Override protected String getMainFunction(){return "SDL_main";}
    @Override protected String[] getArguments(){
        try{return new String[]{DataFiles.root(this).getAbsolutePath(),getApplicationInfo().nativeLibraryDir,
                getIntent().getBooleanExtra("without-mods",false)?"safe":"normal"};}
        catch(java.io.IOException error){throw new IllegalStateException(error.getMessage(),error);}
    }
    @Override public void loadLibraries(){super.loadLibraries();librariesReady=true;}
    @Override protected void onCreate(Bundle saved){
        CrashReports.install(this);
        super.onCreate(saved);
        if(!librariesReady)return;
        immersive();
        View content=SDLActivity.getContentView();
        if(content instanceof ViewGroup){
            overlay=new TouchOverlay(this,GameActivity::nativeMenu,this::touchOptions);
            ((ViewGroup)content).addView(overlay,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
    private void immersive(){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams p=getWindow().getAttributes();p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(p);}
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
    }
    private void touchOptions(){
        if(overlay==null)return;
        String[] options={overlay.isPadVisible()?"Hide touch controls":"Show touch controls","Move controls (pause the game first)",
                overlay.isFloating()?"Use fixed movement stick":"Use floating movement stick","Change control size","Change opacity","Reset current screen layout","Menu size","Face-button layout","Exit game"};
        new AlertDialog.Builder(this).setTitle("Touch controls").setItems(options,(dialog,which)->{
            switch(which){case 0:overlay.toggleVisible();break;case 1:overlay.editLayout();break;case 2:overlay.toggleFloating();break;
                case 3:overlay.changeScale();break;case 4:overlay.changeOpacity();break;case 5:overlay.resetLayout();break;case 6:menuSize();break;
                case 7:new AlertDialog.Builder(this).setTitle("Face buttons").setItems(new String[]{"ABXY (Nintendo)","BAYX (Xbox)","GameCube"},(d,w)->overlay.setFaceLayout(w)).show();break;
                case 8:confirmExit();break;}
        }).setNegativeButton("Close",null).show();
    }
    private void menuSize(){
        String[] labels={"100%","125%","145% (default)","175%","200%","250%","300%"};
        float[] scales={1f,1.25f,1.45f,1.75f,2f,2.5f,3f};
        new AlertDialog.Builder(this).setTitle("Menu size").setItems(labels,(dialog,which)->nativeMenuScale(scales[which])).show();
    }
    private void confirmExit(){
        new AlertDialog.Builder(this).setTitle("Exit combined game?").setMessage("Save in the game before exiting. Unsaved progress is not guaranteed to be preserved.")
                .setPositiveButton("Exit",(dialog,which)->nativeQuit()).setNegativeButton("Keep playing",null).show();
    }
    @Override public void onBackPressed(){if(librariesReady)nativeMenu();else super.onBackPressed();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(librariesReady && event.getKeyCode()==KeyEvent.KEYCODE_BUTTON_SELECT){
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)nativeMenu();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onPause(){uiHandler.removeCallbacks(updateControls);if(overlay!=null)overlay.release();super.onPause();}
    @Override protected void onResume(){super.onResume();if(librariesReady){immersive();uiHandler.removeCallbacks(updateControls);uiHandler.post(updateControls);}}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus && overlay!=null)overlay.release();if(focus && librariesReady)immersive();}
    @Override protected void onDestroy(){
        boolean finish=isFinishing() && !isChangingConfigurations();
        uiHandler.removeCallbacks(updateControls);if(overlay!=null)overlay.release(); super.onDestroy();
        // Only this activity's :game process, never setup or the standalone 2S2H app.
        if(finish)android.os.Process.killProcess(android.os.Process.myPid());
    }
}
