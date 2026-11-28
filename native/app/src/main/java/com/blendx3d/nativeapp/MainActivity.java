package com.blendx3d.nativeapp;
import android.app.Activity;
import android.os.Bundle;
import android.opengl.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
 GLSurfaceView view; Engine engine=new Engine(); LinearLayout bar; TextView status; SeekBar timeline;
 void action(Runnable r){view.queueEvent(r);view.requestRender();}
 void button(LinearLayout row,String name,Runnable r){Button b=new Button(this);b.setText(name);b.setAllCaps(false);row.addView(b);b.setOnClickListener(v->r.run());}
 LinearLayout strip(LinearLayout root){HorizontalScrollView sc=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);sc.addView(row);root.addView(sc);return row;}
 @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff171c25);setContentView(root);
 status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("BlendX3D • Native editor");root.addView(status);
 LinearLayout top=strip(root);view=new GLSurfaceView(this);view.setEGLContextClientVersion(2);view.setRenderer(engine);view.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
 button(top,"Cube",()->action(()->engine.add(false)));button(top,"Sphere",()->action(()->engine.add(true)));button(top,"Next object",()->action(()->engine.selected=(engine.selected+1)%engine.objects.size()));button(top,"Delete",()->action(()->{if(engine.objects.size()>1){engine.objects.remove(engine.selected);engine.selected=0;}}));
 button(top,"Orbit",()->mode(0));button(top,"Move",()->mode(1));button(top,"Rotate",()->mode(2));button(top,"Scale",()->mode(3));button(top,"Sculpt",()->mode(4));button(top,"Bone pose",()->mode(5));
 root.addView(view,new LinearLayout.LayoutParams(-1,0,1));LinearLayout bottom=strip(root);
 button(bottom,"Subdivide",()->action(()->engine.current().subdivide()));button(bottom,"Color",()->action(()->{Mesh m=engine.current();m.color=(m.color+1)%3;}));button(bottom,"Keyframe",()->action(()->engine.current().keys.put(engine.frame,engine.current().pose())));button(bottom,"Play / stop",()->{playing=!playing;if(playing)tick();});button(bottom,"Save PNG",()->action(()->engine.capture=true));
 timeline=new SeekBar(this);timeline.setMax(120);root.addView(timeline);timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){action(()->engine.seek(p));}});
 view.setOnTouchListener(new View.OnTouchListener(){float x,y,d;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getActionMasked()==0||e.getActionMasked()==5){x=e.getX();y=e.getY();d=0;return true;}if(e.getActionMasked()==2){float dx=e.getX()-x,dy=e.getY()-y;x=e.getX();y=e.getY();if(e.getPointerCount()>1){float n=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));float ratio=d>0?d/n:1;d=n;action(()->engine.distance=Math.max(2,Math.min(30,engine.distance*ratio)));}else action(()->engine.drag(dx,dy));}return true;}});
 }
 boolean playing;void tick(){if(!playing)return;timeline.setProgress((timeline.getProgress()+1)%121);timeline.postDelayed(()->tick(),42);}
 void mode(int n){status.setText("BlendX3D • "+new String[]{"Orbit","Move","Rotate","Scale","Sculpt","Bone pose"}[n]);action(()->engine.mode=n);}
 @Override protected void onPause(){playing=false;view.onPause();super.onPause();} @Override protected void onResume(){super.onResume();view.onResume();}
 static class Mesh {
 ArrayList<float[]> tris=new ArrayList<>();float x,y,z,rot,scale=1,bone;int color;TreeMap<Integer,float[]> keys=new TreeMap<>();
 float[] pose(){return new float[]{x,y,z,rot,scale,bone};}void pose(float[] p){x=p[0];y=p[1];z=p[2];rot=p[3];scale=p[4];bone=p[5];}
 void tri(float[] a,float[] b,float[] c){tris.add(a.clone());tris.add(b.clone());tris.add(c.clone());}
 Mesh(boolean sphere){if(sphere){for(int i=0;i<16;i++)for(int j=0;j<24;j++){float[] a=point(i,j),b=point(i+1,j),c=point(i+1,j+1),d=point(i,j+1);tri(a,b,c);tri(a,c,d);}}else{float[][] v={{-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1},{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}};int[][] f={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};for(int[] q:f){tri(v[q[0]],v[q[1]],v[q[2]]);tri(v[q[0]],v[q[2]],v[q[3]]);}}}
 float[] point(int i,int j){double a=Math.PI*i/16,b=2*Math.PI*j/24;return new float[]{(float)(Math.sin(a)*Math.cos(b)),(float)Math.cos(a),(float)(Math.sin(a)*Math.sin(b))};}
 float[] mid(float[] a,float[] b){return new float[]{(a[0]+b[0])/2,(a[1]+b[1])/2,(a[2]+b[2])/2};}
 void subdivide(){if(tris.size()>100000)return;ArrayList<float[]> old=tris;tris=new ArrayList<>();for(int i=0;i<old.size();i+=3){float[] a=old.get(i),b=old.get(i+1),c=old.get(i+2),d=mid(a,b),e=mid(b,c),f=mid(c,a);tri(a,d,f);tri(d,b,e);tri(f,e,c);tri(d,e,f);}}
 }
 class Engine implements GLSurfaceView.Renderer {
 ArrayList<Mesh> objects=new ArrayList<>();int selected,mode,frame,program,w,h;float yaw=35,pitch=20,distance=7;boolean capture;float[] projection=new float[16],camera=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];
 Engine(){objects.add(new Mesh(false));}Mesh current(){return objects.get(selected);}void add(boolean sphere){Mesh m=new Mesh(sphere);m.x=objects.size()*1.5f;objects.add(m);selected=objects.size()-1;}
 void drag(float dx,float dy){Mesh m=current();switch(mode){case 0:yaw+=dx*.5f;pitch=Math.max(-85,Math.min(85,pitch+dy*.5f));break;case 1:m.x+=dx*.01f;m.y-=dy*.01f;break;case 2:m.rot+=dx;break;case 3:m.scale=Math.max(.05f,m.scale*(float)Math.exp(-dy*.01));break;case 4:for(float[] p:m.tris){float weight=Math.max(0,p[1]);p[1]+=weight*(-dy)*.002f;}break;case 5:m.bone+=dx;break;}}
 void seek(int f){frame=f;for(Mesh m:objects){if(m.keys.isEmpty())continue;Map.Entry<Integer,float[]> a=m.keys.floorEntry(f),b=m.keys.ceilingEntry(f);if(a==null)a=b;if(b==null)b=a;float t=a.getKey().equals(b.getKey())?0:(float)(f-a.getKey())/(b.getKey()-a.getKey());float[] p=a.getValue().clone();for(int i=0;i<p.length;i++)p[i]+=(b.getValue()[i]-p[i])*t;m.pose(p);}}
 int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
 public void onSurfaceCreated(GL10 g,EGLConfig c){GLES20.glEnable(GLES20.GL_DEPTH_TEST);program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,"attribute vec3 p; attribute vec3 n; uniform mat4 mvp; uniform mat4 mv; varying float light; void main(){gl_Position=mvp*vec4(p,1.0);light=0.25+0.75*max(0.0,dot(normalize(mat3(mv)*n),normalize(vec3(0.4,0.8,1.0))));}"));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying float light; uniform vec3 color; void main(){gl_FragColor=vec4(color*light,1.0);}"));GLES20.glLinkProgram(program);int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));}
 public void onSurfaceChanged(GL10 g,int width,int height){w=width;h=height;GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,45,(float)w/Math.max(1,h),.1f,100);}
 FloatBuffer buffer(float[] a){FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).position(0);return b;}
 float[] deform(float[] p,float angle){double a=Math.toRadians(angle*Math.max(0,p[1]));return new float[]{(float)(p[0]*Math.cos(a)-p[1]*Math.sin(a)),(float)(p[0]*Math.sin(a)+p[1]*Math.cos(a)),p[2]};}
 public void onDrawFrame(GL10 g){GLES20.glClearColor(.09f,.12f,.17f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);double a=Math.toRadians(yaw),b=Math.toRadians(pitch);Matrix.setLookAtM(camera,0,(float)(distance*Math.cos(b)*Math.sin(a)),(float)(distance*Math.sin(b)),(float)(distance*Math.cos(b)*Math.cos(a)),0,0,0,0,1,0);
 for(Mesh m:objects){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,m.x,m.y,m.z);Matrix.rotateM(model,0,m.rot,0,1,0);Matrix.scaleM(model,0,m.scale,m.scale,m.scale);Matrix.multiplyMM(mv,0,camera,0,model,0);Matrix.multiplyMM(mvp,0,projection,0,mv,0);float[] p=new float[m.tris.size()*3],n=p.clone();for(int i=0;i<m.tris.size();i+=3){float[] u=deform(m.tris.get(i),m.bone),v=deform(m.tris.get(i+1),m.bone),t=deform(m.tris.get(i+2),m.bone);float ax=v[0]-u[0],ay=v[1]-u[1],az=v[2]-u[2],bx=t[0]-u[0],by=t[1]-u[1],bz=t[2]-u[2];float[] norm={ay*bz-az*by,az*bx-ax*bz,ax*by-ay*bx};if(Math.abs(norm[0])+Math.abs(norm[1])+Math.abs(norm[2])<.000001)norm[1]=1;for(int j=0;j<3;j++){System.arraycopy(new float[][]{u,v,t}[j],0,p,(i+j)*3,3);System.arraycopy(norm,0,n,(i+j)*3,3);}}
 int pl=GLES20.glGetAttribLocation(program,"p"),nl=GLES20.glGetAttribLocation(program,"n");GLES20.glEnableVertexAttribArray(pl);GLES20.glEnableVertexAttribArray(nl);GLES20.glVertexAttribPointer(pl,3,GLES20.GL_FLOAT,false,0,buffer(p));GLES20.glVertexAttribPointer(nl,3,GLES20.GL_FLOAT,false,0,buffer(n));GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"mvp"),1,false,mvp,0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"mv"),1,false,mv,0);float[][] colors={{.85f,.45f,.18f},{.25f,.65f,.9f},{.4f,.85f,.5f}};float[] col=colors[m.color];GLES20.glUniform3f(GLES20.glGetUniformLocation(program,"color"),col[0],col[1],col[2]);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.tris.size());}
 if(capture){capture=false;try{IntBuffer pixels=IntBuffer.allocate(w*h);GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);int[] out=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int q=pixels.get(y*w+x);out[(h-1-y)*w+x]=(q&0xff00ff00)|((q&255)<<16)|((q>>16)&255);}android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(out,w,h,android.graphics.Bitmap.Config.ARGB_8888);java.io.File file=new java.io.File(getExternalFilesDir(null),"render-"+System.currentTimeMillis()+".png");try(java.io.FileOutputStream stream=new java.io.FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}runOnUiThread(()->Toast.makeText(MainActivity.this,file.getName(),Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,"Render save failed",Toast.LENGTH_LONG).show());}}
 }
 }
}
