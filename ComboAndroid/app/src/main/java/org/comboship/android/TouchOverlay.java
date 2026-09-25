package org.comboship.android;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
/** Multi-pointer N64 controls; unclaimed touches continue to SDL's surface/ImGui. */
public final class TouchOverlay extends View {
    private static final int LEFT=200, RIGHT=201, MENU=202, Z=203, R=204, CU=210, CD=211, CL=212, CR=213;
    private static final class Control {
        final int id; final String label; float x,y,r;
        Control(int id,String label,float x,float y,float r){this.id=id;this.label=label;this.x=x;this.y=y;this.r=r;}
    }
    private static final class Finger {
        final Control control; final float originX,originY; float x,y; boolean longPress;
        Finger(Control c,float x,float y){control=c;originX=x;originY=y;this.x=x;this.y=y;}
    }
    private final List<Control> controls=new ArrayList<>();
    private final Map<Integer,Finger> fingers=new HashMap<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final Runnable menu,options;
    private boolean showPad,editing,floating,suppressed;
    private float opacity,scale;
    private String profile="wide";
    private int faceLayout;
    public TouchOverlay(Context context,Runnable menu,Runnable options){
        super(context);this.menu=menu;this.options=options;
        preferences=context.getSharedPreferences("combo-touch",Context.MODE_PRIVATE);
        showPad=preferences.getBoolean("show",true);floating=preferences.getBoolean("floating",false);
        opacity=preferences.getFloat("opacity",.48f);scale=preferences.getFloat("scale",1f);
        faceLayout=preferences.getInt("face-layout",0);
        setContentDescription("Touch gamepad. Tap Menu for game settings; hold Menu for touch options.");setFocusable(false);
    }
    private void build(){
        if(getWidth()==0 || getHeight()==0)return;
        release();controls.clear();profile=(getWidth()>getHeight()*1.65f?"wide":"fold")+"."+faceLayout;
        float unit=Math.min(getWidth(),getHeight());
        float r=Math.max(23*getResources().getDisplayMetrics().density,unit*.052f)*scale;
        add(MENU,editing?"Done":"Menu",.5f,.10f,r*1.2f);
        add(LEFT,"Move",.16f,.73f,r*2.0f);add(RIGHT,"Camera",.64f,.77f,r*1.5f);
        if(faceLayout==2){
            add(0,"A",.88f,.86f,r*1.2f);add(1,"B",.77f,.91f,r*.8f);
            add(2,"X",.96f,.73f,r*.8f);add(3,"Y",.84f,.66f,r*.8f);
        }else if(faceLayout==1){
            add(0,"A",.86f,.91f,r);add(1,"B",.94f,.81f,r);
            add(2,"X",.78f,.81f,r);add(3,"Y",.86f,.71f,r);
        }else{
            add(0,"A",.94f,.81f,r);add(1,"B",.86f,.91f,r);
            add(2,"X",.86f,.71f,r);add(3,"Y",.78f,.81f,r);
        }
        add(9,"L",.18f,.30f,r);add(Z,"Z",.07f,.30f,r);add(R,"R",.91f,.16f,r);
        add(6,"Start",.48f,.87f,r*.95f);
        add(11,"↑",.35f,.61f,r*.76f);add(12,"↓",.35f,.79f,r*.76f);
        add(13,"←",.30f,.70f,r*.76f);add(14,"→",.40f,.70f,r*.76f);
        add(CU,"C↑",.90f,.29f,r*.80f);add(CD,"C↓",.90f,.49f,r*.80f);
        add(CL,"C←",.835f,.39f,r*.80f);add(CR,"C→",.965f,.39f,r*.80f);invalidate();
    }
    private void add(int id,String label,float x,float y,float r){
        Control c=new Control(id,label,x,y,r);
        if(id!=MENU){c.x=preferences.getFloat(profile+"."+id+".x",x);c.y=preferences.getFloat(profile+"."+id+".y",y);}
        c.x=clamp(c.x,r/getWidth(),1-r/getWidth());c.y=clamp(c.y,r/getHeight(),1-r/getHeight());controls.add(c);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);build();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        for(Control c:controls){
            if((!showPad || suppressed) && c.id!=MENU && !editing)continue;
            float x=c.x*getWidth(),y=c.y*getHeight();boolean pressed=false;
            for(Finger finger:fingers.values()) if(finger.control==c){
                pressed=true;if(c.id==LEFT && floating && !editing){x=finger.originX;y=finger.originY;}break;
            }
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.BLACK);paint.setAlpha((int)(opacity*170));canvas.drawCircle(x,y,c.r,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(editing?4:2);paint.setColor(Color.WHITE);paint.setAlpha((int)(255*opacity));canvas.drawCircle(x,y,c.r,paint);
            if(c.id==LEFT || c.id==RIGHT){
                float dx=0,dy=0;
                for(Finger f:fingers.values())if(f.control==c){float[] a=stick(f);dx=a[0]*c.r*.55f;dy=a[1]*c.r*.55f;}
                paint.setStyle(Paint.Style.FILL);paint.setAlpha((int)(opacity*190));canvas.drawCircle(x+dx,y+dy,c.r*.36f,paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setAlpha(pressed?255:(int)(opacity*255));
            paint.setTextSize(Math.min(c.r*.66f,18*getResources().getDisplayMetrics().scaledDensity));paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(c.label,x,y-(paint.ascent()+paint.descent())/2,paint);
        }
        if(editing){paint.setColor(Color.WHITE);paint.setAlpha(255);paint.setTextSize(16*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Drag controls. Tap Done to save this screen layout.",getWidth()*.5f,getHeight()*.22f,paint);}
    }
    private Control hit(float x,float y){
        for(int i=controls.size()-1;i>=0;i--){Control c=controls.get(i);
            if((!showPad || suppressed) && c.id!=MENU && !editing)continue;
            if(Math.hypot(x-c.x*getWidth(),y-c.y*getHeight())<=c.r*1.1f)return c;
        }
        if(floating && showPad && !suppressed && !editing && x<getWidth()*.43f && y>getHeight()*.35f){
            for(Control c:controls)if(c.id==LEFT)return c;
        }
        return null;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN){
            Control c=hit(event.getX(index),event.getY(index));if(c==null)return !fingers.isEmpty();
            Finger f=new Finger(c,event.getX(index),event.getY(index));fingers.put(id,f);
            if(c.id==MENU && !editing)handler.postDelayed(()->{if(fingers.get(id)==f){f.longPress=true;release();options.run();}},550);
        }else if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getPointerCount();i++){
                Finger f=fingers.get(event.getPointerId(i));if(f==null)continue;f.x=event.getX(i);f.y=event.getY(i);
                if(editing && f.control.id!=MENU){f.control.x=clamp(f.x/getWidth(),.025f,.975f);f.control.y=clamp(f.y/getHeight(),.04f,.96f);}
            }
        }else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP){
            Finger f=fingers.remove(id);
            if(f!=null && f.control.id==MENU && !f.longPress){
                if(editing){saveLayout();editing=false;build();}else menu.run();performClick();
            }
        }else if(action==MotionEvent.ACTION_CANCEL){release();return true;}
        publish();invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private float[] stick(Finger f){
        float x=(f.control.id==LEFT && floating)?f.originX:f.control.x*getWidth();
        float y=(f.control.id==LEFT && floating)?f.originY:f.control.y*getHeight();
        float dx=(f.x-x)/f.control.r,dy=(f.y-y)/f.control.r;float length=(float)Math.hypot(dx,dy);
        if(length<.10f)return new float[]{0,0};if(length>1){dx/=length;dy/=length;}return new float[]{dx,dy};
    }
    private static float clamp(float f,float min,float max){return Math.max(min,Math.min(max,f));}
    private void publish(){
        int buttons=0;float lx=0,ly=0,rx=0,ry=0,lt=0,rt=0;
        if(!editing)for(Finger f:fingers.values()){
            int id=f.control.id;
            if(id==LEFT){float[] a=stick(f);lx=a[0];ly=a[1];}
            else if(id==RIGHT){float[] a=stick(f);rx=a[0];ry=a[1];}
            else if(id==Z)lt=1;else if(id==R)rt=1;else if(id<21)buttons|=1<<id;
        }
        // Explicit C buttons override the camera's shared default right stick.
        if(!editing)for(Finger f:fingers.values()){
            if(f.control.id==CU)ry=-1;else if(f.control.id==CD)ry=1;else if(f.control.id==CL)rx=-1;else if(f.control.id==CR)rx=1;
        }
        GameActivity.nativePad(buttons,lx,ly,rx,ry,lt,rt);
    }
    public void release(){fingers.clear();handler.removeCallbacksAndMessages(null);GameActivity.nativeRelease();invalidate();}
    public void toggleVisible(){showPad=!showPad;preferences.edit().putBoolean("show",showPad).apply();release();invalidate();}
    public void toggleFloating(){floating=!floating;preferences.edit().putBoolean("floating",floating).apply();release();}
    public boolean isFloating(){return floating;}
    public void setFaceLayout(int layout){faceLayout=Math.max(0,Math.min(2,layout));preferences.edit().putInt("face-layout",faceLayout).apply();build();}
    public void setSuppressed(boolean value){if(suppressed!=value){suppressed=value;release();invalidate();}}
    public boolean isPadVisible(){return showPad;}
    public void editLayout(){showPad=true;editing=true;build();}
    public void changeScale(){scale=scale>=1.3f?.8f:scale+.15f;preferences.edit().putFloat("scale",scale).apply();build();}
    public void changeOpacity(){opacity=opacity>=.85f?.25f:opacity+.2f;preferences.edit().putFloat("opacity",opacity).apply();invalidate();}
    private void saveLayout(){
        SharedPreferences.Editor editor=preferences.edit();
        for(Control c:controls)if(c.id!=MENU){editor.putFloat(profile+"."+c.id+".x",c.x);editor.putFloat(profile+"."+c.id+".y",c.y);}editor.apply();
    }
    public void resetLayout(){
        SharedPreferences.Editor editor=preferences.edit();
        for(Control c:controls){editor.remove(profile+"."+c.id+".x");editor.remove(profile+"."+c.id+".y");}editor.apply();build();
    }
}
