import java.lang.reflect.Method;
public class FlairProbe {
 public static void main(String[] args) throws Exception {
  Class<?> type = Class.forName("com.rubenmayayo.reddit.ui.customviews.m");
  Method bg = type.getMethod("catppuccinFlairBackground", int.class);
  Method fg = type.getMethod("catppuccinFlairText", int.class, int.class);
  int[] inputs={0xffff0000,0xff00ff00,0xff0000ff,0xffffa500,0xff808080,0};
  int[][] expected={{0xffd20f39,0xff40a02b,0xff1e66f5,0xfffe640b,0xffccd0da,0},{0xffed8796,0xffa6da95,0xff8aadf4,0xfff5a97f,0xff363a4f,0}};
  for (int theme : new int[]{0,17,18}) {
   id.b.theme=theme;
   for (int i=0;i<inputs.length;i++) {
    int result=(Integer)bg.invoke(null,inputs[i]);
    int text=(Integer)fg.invoke(null,inputs[i],0xffffffff);
    int want=theme==0?inputs[i]:expected[theme-17][i];
    if(result!=want)throw new AssertionError(Integer.toHexString(result));
    if(theme==0 && text!=0xffffffff)throw new AssertionError("Stock text changed");
    System.out.printf("PASS theme=%d input=%08x background=%08x text=%08x%n",theme,inputs[i],result,text);
   }
  }
 }
}
