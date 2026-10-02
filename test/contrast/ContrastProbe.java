import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import com.google.android.material.chip.Chip;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
public class ContrastProbe {
 public static class Follow extends Chip {
  int text,icon,background,stroke;
  Follow(){super(null);}
  public void setTextColor(int c){text=c;}
  public void setChipIconTint(ColorStateList c){icon=c.getDefaultColor();}
  public void setChipBackgroundColor(ColorStateList c){background=c.getDefaultColor();}
  public void setChipStrokeColor(ColorStateList c){stroke=c.getDefaultColor();}
 }
 public static class Refresh extends SwipeRefreshLayout {
  int background,color,original;
  Refresh(){super(null);}
  public void setProgressBackgroundColorSchemeColor(int c){background=c;}
  public void setColorSchemeColors(int... c){color=c[0];}
  public void setColorSchemeResources(int... c){original=c[0];}
 }
 public static class Pattern extends ImageView {
  int id;ColorFilter filter;
  Pattern(){super(null);}
  public int getId(){return id;}
  public void setColorFilter(ColorFilter f){filter=f;}
 }
 static Object allocate(Class<?> type)throws Exception {
  Class<?> u=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
  return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
 }
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){try{run();}catch(Throwable e){e.printStackTrace(System.out);}}
 static void run()throws Exception {
  Class<?> utils=Class.forName("he.f0");
  for(int theme:new int[]{0,17,18}) {
   id.b.theme=theme;Follow follow=(Follow)allocate(Follow.class);Refresh refresh=(Refresh)allocate(Refresh.class);
   Pattern pattern=(Pattern)allocate(Pattern.class);pattern.id=0x7f0a0383;
   utils.getMethod("catppuccinFollow",Chip.class).invoke(null,follow);
   utils.getMethod("catppuccinRefresh",SwipeRefreshLayout.class,int[].class).invoke(null,refresh,new int[]{1234});
   utils.getMethod("catppuccinPattern",ImageView.class).invoke(null,pattern);
   int style=(Integer)utils.getMethod("catppuccinToolbarStyle",int.class).invoke(null,1234);
   if(theme==0){require(follow.background==0 && pattern.filter==null && refresh.original==1234 && style==1234,"Stock theme changed");}
   else {
    int mauve=theme==17?0xff8839ef:0xffc6a0f6, ink=theme==17?0xffeff1f5:0xff24273a;
    require(follow.background==mauve && follow.stroke==mauve && follow.text==ink && follow.icon==ink,"Follow palette");
    require(refresh.color==mauve && refresh.background==(theme==17?0xffccd0da:0xff363a4f),"Refresh palette");
    require(style==(theme==17?0x7f141003:0x7f141006),"Toolbar overlay");
    require(pattern.filter instanceof ColorMatrixColorFilter,"Pattern filter missing");
    android.graphics.ColorMatrix cm=new android.graphics.ColorMatrix();
    ((ColorMatrixColorFilter)pattern.filter).getColorMatrix(cm);float[] matrix=cm.getArray();
    int[] low=theme==17?new int[]{204,208,218}:new int[]{30,32,48};
    int[] high=theme==17?new int[]{220,224,232}:new int[]{73,77,100};
    for(int row=0;row<3;row++){
     float slope=matrix[row*5]+matrix[row*5+1]+matrix[row*5+2];
     require(Math.abs(99*slope+matrix[row*5+4]-low[row])<0.1f,"Pattern low shade");
     require(Math.abs(252*slope+matrix[row*5+4]-high[row])<0.1f,"Pattern high shade");
    }
   }
   pattern=(Pattern)allocate(Pattern.class);pattern.id=42;
   utils.getMethod("catppuccinPattern",ImageView.class).invoke(null,pattern);
   require(pattern.filter==null,"Unrelated image recolored");
   System.out.println("PASS theme="+theme+" follow, refresh, toolbar, header pattern");
  }
 }
}
