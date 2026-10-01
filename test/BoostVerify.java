public class BoostVerify {
 public static void main(String[] args) throws Exception {
  for (String name : args) {
   Class<?> c = Class.forName(name, false, BoostVerify.class.getClassLoader());
   System.out.println("VERIFIED " + name + " methods=" + c.getDeclaredMethods().length);
  }
 }
}
