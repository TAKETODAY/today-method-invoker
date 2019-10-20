# Java Byte Code Level Method Invoker

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/27df9e2cafa247acb9cae634a17b6044)](https://www.codacy.com/manual/TAKETODAY/today-method-invoker?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=TAKETODAY/today-method-invoker&amp;utm_campaign=Badge_Grade)


## 使用说明

```java
public class TestHandlerInvoker {

	public static void main(String[] args) throws Exception {

        System.setProperty("cglib.debugLocation", "D:/debug");
        {
            final Method main = Bean.class.getDeclaredMethod("main");
            final Invoker mainInvoker = MethodInvokerCreator.create(main);
            mainInvoker.invoke(null, null);
        }
        {
            final Method test = Bean.class.getDeclaredMethod("test", short.class);
            final Invoker mainInvoker = MethodInvokerCreator.create(test);
            mainInvoker.invoke(null, new Object[] { (short) 1 });
        }

        final Invoker create = MethodInvokerCreator.create(Bean.class, "test");
        create.invoke(new Bean(), null);
        final Invoker itself = MethodInvokerCreator.create(Bean.class, "test", Bean.class);
        itself.invoke(new Bean(), new Object[] { new Bean() });
    }

    public static class Bean {

        public static void test(short i) throws Throwable {
            System.err.println("static main " + i);
        }

        protected static void main() throws Throwable {
            System.err.println("static main");
        }

        public void test() throws Throwable {
            System.err.println("instance test");
        }

        void test(Bean itself) {
            System.err.println("instance test :" + itself);
        }
    }
}

```


## 🙏 鸣谢

本项目的诞生离不开以下项目：
* [ASM](https://asm.ow2.io): ASM is an all purpose Java bytecode manipulation and analysis framework
* [Cglib](https://github.com/cglib/cglib): Byte Code Generation Library


## 📄 开源协议

使用 [GNU GENERAL PUBLIC LICENSE](https://github.com/TAKETODAY/today-method-invoker/blob/master/LICENSE) 开源协议

