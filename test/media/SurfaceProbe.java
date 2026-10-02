import android.view.View;
import java.util.HashMap;
public class SurfaceProbe {
 public static class ProbeView extends View {
  int color;
  HashMap<Integer,View> children;
  private ProbeView(){super(null);}
  public void setBackgroundColor(int value){color=value;}
  protected View findViewTraversal(int id){return children.get(id);}
 }
 static ProbeView view() throws Exception {
  Class<?> u=Class.forName("sun.misc.Unsafe");
  java.lang.reflect.Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
  ProbeView v=(ProbeView)u.getMethod("allocateInstance",Class.class).invoke(f.get(null),ProbeView.class);
  v.children=new HashMap<>();v.color=0xff123456;return v;
 }
 static void color(ProbeView v,int want){if(v.color!=want)throw new AssertionError(Integer.toHexString(v.color));}
 public static void main(String[] args){try{run();}catch(Throwable e){e.printStackTrace(System.out);}}
 static void run() throws Exception {
  Class<?> utils=Class.forName("he.f0");
  for(int theme:new int[]{0,17,18}) {
   id.b.theme=theme;
   int wanted=theme==17?0xffeff1f5:theme==18?0xff24273a:0xff123456;
   ProbeView media=view();
   for(int id:new int[]{0x7f0a01ac,0x7f0a0374,0x7f0a06be,0x7f0a06bb,0x7f0a0243,0x7f0a06ba})media.children.put(id,view());
   utils.getMethod("catppuccinMediaSurface",View.class).invoke(null,media);
   color(media,wanted);for(View v:media.children.values())color((ProbeView)v,wanted);
   ProbeView post=view();
   for(int id:new int[]{0x7f0a05b2,0x7f0a05c0})post.children.put(id,view());
   utils.getMethod("catppuccinPostBody",View.class).invoke(null,post);
   color(post,0xff123456);for(View v:post.children.values())color((ProbeView)v,wanted);
   System.out.println("PASS theme="+theme+" media/page/player surfaces and text-post body backgrounds");
  }
 }
}
