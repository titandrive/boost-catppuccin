import android.app.Activity;
import android.view.View;
public class ToolbarProbe {
 public static class Toolbar extends View {
  int color;float elevation;
  private Toolbar(){super(null);}
  public void setBackgroundColor(int c){color=c;}
  public void setElevation(float e){elevation=e;}
 }
 public static class Viewer extends Activity {
  Toolbar toolbar;
  public <T extends View> T findViewById(int id){if(id!=0x7f0a0434)throw new AssertionError("Wrong toolbar id");return (T)toolbar;}
 }
 static Object allocate(Class<?> type)throws Exception {
  Class<?> u=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
  return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
 }
 public static void main(String[] args){try{run();}catch(Throwable e){e.printStackTrace(System.out);}}
 static void run()throws Exception {
  Class<?> utils=Class.forName("he.f0");
  for(int theme:new int[]{0,17,18}) {
   id.b.theme=theme;Viewer activity=(Viewer)allocate(Viewer.class);Toolbar toolbar=(Toolbar)allocate(Toolbar.class);
   activity.toolbar=toolbar;toolbar.color=0xff123456;toolbar.elevation=8f;
   utils.getMethod("catppuccinMediaToolbar",Activity.class).invoke(null,activity);
   if(toolbar.color!=(theme==0?0xff123456:0) || toolbar.elevation!=(theme==0?8f:0f))throw new AssertionError("Toolbar gradient retained");
   System.out.println("PASS theme="+theme+" gallery toolbar transparency/elevation");
  }
 }
}
