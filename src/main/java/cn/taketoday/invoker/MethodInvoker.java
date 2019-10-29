/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2019 All Rights Reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *   
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */
package cn.taketoday.invoker;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * @author TODAY <br>
 *         2019-10-18 22:35
 */
public abstract class MethodInvoker implements Invoker {

    @Override
    public abstract Object invoke(Object obj, Object[] args);

    /**
     * Create a {@link MethodInvoker}
     * 
     * @param method
     *            Target method to invoke
     * @return {@link MethodInvoker} sub object
     */
    public static MethodInvoker create(Method mapping) {
        return new MethodInvokerGenerator(mapping).create();
    }

    /**
     * Create a {@link MethodInvoker}
     * 
     * @param beanClass
     *            Bean Class
     * @param name
     *            Target method to invoke
     * @param parameters
     *            Target parameters classes
     * @throws NoSuchMethodException
     *             Thrown when a particular method cannot be found.
     * 
     * @return {@link MethodInvoker} sub object
     */
    public static MethodInvoker create(final Class<?> beanClass,
                                       final String name, final Class<?>... parameterClasses) throws NoSuchMethodException {

        final Method targetMethod = beanClass.getDeclaredMethod(name, parameterClasses);

        return new MethodInvokerGenerator(targetMethod, beanClass).create();
    }

    // MethodInvoker object generator
    // --------------------------------------------------------------

    public static class MethodInvokerGenerator {

        public static final Type TYPE_LONG = Type.getType(Long.class);
        public static final Type TYPE_BYTE = Type.getType(Byte.class);
        public static final Type TYPE_FLOAT = Type.getType(Float.class);
        public static final Type TYPE_SHORT = Type.getType(Short.class);
        public static final Type TYPE_DOUBLE = Type.getType(Double.class);
        public static final Type TYPE_BOOLEAN = Type.getType(Boolean.class);
        public static final Type TYPE_INTEGER = Type.getType(Integer.class);
        public static final Type TYPE_CHARACTER = Type.getType(Character.class);

        private static final String SOURCE_FILE = "<generated>";
        private static final String superType = "cn/taketoday/invoker/MethodInvoker";
        private static final String[] interfaces = { "cn/taketoday/invoker/Invoker" };
        private static final String invokeDescriptor = "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";

        // loader
        // -----------------------------------------

        private static final Object UNSAFE;
        private static final Throwable THROWABLE;
        private static Method DEFINE_CLASS;
        private static Method DEFINE_CLASS_UNSAFE;
        private static final ProtectionDomain PROTECTION_DOMAIN;

        // generator
        // ------------------------------------------
        private String className;
        private final Class<?> targetClass;
        private final Method targetMethod;

        public MethodInvokerGenerator(Method method) {
            this(method, method.getDeclaringClass());
        }

        public MethodInvokerGenerator(Method method, Class<?> targetClass) {
            this.targetMethod = method;
            this.targetClass = targetClass;
        }

        protected ProtectionDomain getProtectionDomain() {
            return getProtectionDomain(targetClass);
        }

        /**
         * Create {@link MethodInvoker} sub object
         * 
         * @return {@link MethodInvoker} sub object
         */
        public MethodInvoker create() {
            try {
                return generateClass().getDeclaredConstructor().newInstance();
            }
            catch (ReflectiveOperationException e) {
                throw new InvokerCreateException(e);
            }
        }

        /**
         * Generate Class sub class
         * 
         * @return {@link MethodInvoker} sub class
         */
        protected Class<MethodInvoker> generateClass() {

            try {
                final ClassLoader classLoader = targetClass.getClassLoader();
                if (classLoader == null) {
                    throw new IllegalStateException("ClassLoader is null while trying to define class " + getClassName()
                            + ". It seems that the loader has been expired from a weak reference somehow. "
                            + "Please file an issue at cglib's issue tracker.");
                }

                DefaultClassWriter classWriter = new DefaultClassWriter(getClassName());
                generateClass(classWriter);
                final byte[] b = classWriter.toByteArray();

                return defineClass(b, getClassName(), classLoader, getProtectionDomain());
            }
            catch (RuntimeException | Error e) {
                throw e;
            }
            catch (Exception e) {
                throw new InvokerCreateException(e);
            }
        }

        public void generateClass(ClassVisitor cv) throws NoSuchMethodException {

            cv.visit(Opcodes.V1_8, ACC_PUBLIC | ACC_FINAL, getClassName().replace('.', '/'), null, superType, interfaces);
            cv.visitSource(SOURCE_FILE, null);

            emptyConstructor(cv);

            MethodVisitor methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "invoke", invokeDescriptor, null, null);

            final int modifiers = targetMethod.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                throw new InvokerCreateException("Can't access to a private method");
            }
            if (!Modifier.isStatic(modifiers)) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
                checkcast(methodVisitor, targetClass);
                // methodVisitor.visitInsn(Opcodes.DUP);
            }

            if (targetMethod.getParameterCount() != 0) {
                resolveParameter(methodVisitor);
            }

            invokeTargetMethod(methodVisitor);

            returnValue(methodVisitor);

            // end method
            if (!Modifier.isAbstract(modifiers)) {
                methodVisitor.visitMaxs(0, 0);
            }
            cv.visitEnd(); //end class
        }

        /**
         * Get sub class name
         * 
         * @return sub class name
         */
        protected String getClassName() {
            if (className == null) {
                StringBuilder builder = new StringBuilder(targetClass.getName());
                builder.append('$').append(targetMethod.getName());

                if (targetMethod.getParameterCount() != 0) {

                    for (final Class<?> parameterType : targetMethod.getParameterTypes()) {
                        builder.append('$');
                        if (parameterType.isArray()) {
                            builder.append("A$");
                            final String simpleName = parameterType.getSimpleName();
                            builder.append(simpleName.substring(0, simpleName.length() - 2));
                        }
                        else {
                            builder.append(parameterType.getSimpleName());
                        }
                    }
                }
                this.className = builder.toString();
            }
            return className;
        }

        // utils
        // -------------------------------------------------

        /**
         * create a default {@link Constructor}
         * 
         * @param cv
         *            {@link ClassWriter}
         */
        protected static void emptyConstructor(ClassVisitor cv) {

            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);

            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superType, "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
        }

        /**
         * Resolve target method parameters
         * 
         * @param methodVisitor
         *            Target {@link MethodVisitor}
         */
        protected void resolveParameter(MethodVisitor methodVisitor) {

            final Class<?>[] parameterTypes = targetMethod.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                aaload(methodVisitor, i);

                final Class<?> parameterClass = parameterTypes[i];
                if (parameterClass.isPrimitive()) {
                    unbox(methodVisitor, parameterClass);
                }
                else {
                    checkcast(methodVisitor, parameterClass);
                }
            }
        }

        protected void unbox(MethodVisitor methodVisitor, final Class<?> parameterClass) {
            final Type parameterType = Type.getType(parameterClass);
            final Type boxedType = getBoxedType(parameterType); // java.lang.Long ...

            checkcast(methodVisitor, boxedType);

            final String name = parameterClass.getName() + "Value";
            final String desc = "()" + parameterType.getDescriptor();

            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, boxedType.getInternalName(), name, desc, false);
        }

        /**
         * If the argument is a primitive class, replaces the primitive value on the top
         * of the stack with the wrapped (Object) equivalent. For example, char ->
         * Character. If the class is Void, a null is pushed onto the stack instead.
         * 
         * @param inputClass
         *            the class indicating the current type of the top stack value
         */
        public void box(final MethodVisitor mv, Class<?> inputClass) {

            if (inputClass.isPrimitive()) {
                if (inputClass == Void.TYPE) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                }
                else {
                    final Type type = Type.getType(inputClass);
                    Type boxed = getBoxedType(type);

                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                       boxed.getInternalName(),
                                       "valueOf",
                                       Type.getMethodDescriptor(boxed, new Type[]
                                       { type }), 
                                       false);

                }
            }
        }

        protected void returnValue(final MethodVisitor mv) {

            box(mv, targetMethod.getReturnType());

            mv.visitInsn(Opcodes.ARETURN);
        }

        protected void invokeTargetMethod(final MethodVisitor mv) {

            mv.visitMethodInsn(Modifier.isStatic(targetMethod.getModifiers()) ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
                               Type.getType(targetClass).getInternalName(),
                               targetMethod.getName(),
                               Type.getMethodDescriptor(targetMethod), false);
        }

        protected void checkcast(final MethodVisitor methodVisitor, final Class<?> targetClass) {

            if (!targetClass.equals(Object.class)) {
                checkcast(methodVisitor, Type.getType(targetClass));
            }
        }

        protected void checkcast(final MethodVisitor methodVisitor, final Type type) {

            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST,
                                        targetClass.isArray() ? type.getDescriptor() : type.getInternalName());
        }

        protected void push(MethodVisitor mv, int i) {
            if (i < -1) {
                mv.visitLdcInsn(Integer.valueOf(i));
            }
            else if (i <= 5) {
                mv.visitInsn(iconst(i));
            }
            else if (i <= Byte.MAX_VALUE) {
                mv.visitIntInsn(Opcodes.BIPUSH, i);
            }
            else if (i <= Short.MAX_VALUE) {
                mv.visitIntInsn(Opcodes.SIPUSH, i);
            }
            else {
                mv.visitLdcInsn(Integer.valueOf(i));
            }
        }

        protected static Type getBoxedType(Type type) {

            switch (type.getSort()) { //@off
                case Type.CHAR :    return TYPE_CHARACTER;
                case Type.BOOLEAN : return TYPE_BOOLEAN;
                case Type.DOUBLE :  return TYPE_DOUBLE;
                case Type.FLOAT :   return TYPE_FLOAT;
                case Type.LONG :    return TYPE_LONG;
                case Type.INT :     return TYPE_INTEGER;
                case Type.SHORT :   return TYPE_SHORT;
                case Type.BYTE :    return TYPE_BYTE;
                default:            return type;
            } //@on
        }

        /**
         * ICONST
         */
        protected static int iconst(int value) { //@off
            switch (value) {
                case -1: return Opcodes.ICONST_M1;
                case 0: return Opcodes.ICONST_0;
                case 1: return Opcodes.ICONST_1;
                case 2: return Opcodes.ICONST_2;
                case 3: return Opcodes.ICONST_3;
                case 4: return Opcodes.ICONST_4;
                case 5: return Opcodes.ICONST_5;
                default: return -1;
            } // error@on
        }

        protected void aaload(MethodVisitor mv, int index) {
            push(mv, index);
            mv.visitInsn(Opcodes.AALOAD);
        }

        public static Type[] getTypes(Class<?>... classes) {
            if (classes == null) {
                return null;
            }
            Type[] types = new Type[classes.length];
            for (int i = 0; i < classes.length; i++) {
                types[i] = Type.getType(classes[i]);
            }
            return types;
        }

        public static String[] toInternalNames(Type[] types) {
            if (types == null) {
                return null;
            }
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = types[i].getInternalName();
            }
            return names;
        }

        // loader
        // ----------------------------------------------------------------

        static {

            Object unsafe;
            Throwable throwable = null;
            ProtectionDomain protectionDomain;
            Method defineClass;
            Method defineClassUnsafe;

            try {

                protectionDomain = getProtectionDomain(MethodInvokerGenerator.class);

                try {
                    defineClass = AccessController.doPrivileged((PrivilegedExceptionAction<Method>) () -> {
                        Method ret = ClassLoader.class.getDeclaredMethod("defineClass",
                                                                         String.class,
                                                                         byte[].class,
                                                                         Integer.TYPE,
                                                                         Integer.TYPE,
                                                                         ProtectionDomain.class);
                        ret.setAccessible(true);
                        return ret;
                    });
                    defineClassUnsafe = null;
                    unsafe = null;
                }
                catch (Throwable t) {
                    // Fallback on Jigsaw where this method is not available.
                    throwable = t;
                    defineClass = null;
                    unsafe = AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                        Class<?> u = Class.forName("sun.misc.Unsafe");
                        Field theUnsafe = u.getDeclaredField("theUnsafe");
                        theUnsafe.setAccessible(true);
                        return theUnsafe.get(null);
                    });
                    Class<?> u = Class.forName("sun.misc.Unsafe");
                    defineClassUnsafe = u.getMethod("defineClass",
                                                    String.class,
                                                    byte[].class,
                                                    Integer.TYPE,
                                                    Integer.TYPE,
                                                    ClassLoader.class,
                                                    ProtectionDomain.class);
                }
            }
            catch (Throwable t) {
                if (throwable == null) throwable = t;
                defineClass = null;
                protectionDomain = null;
                unsafe = defineClassUnsafe = null;
            }
            PROTECTION_DOMAIN = protectionDomain;
            DEFINE_CLASS = defineClass;
            DEFINE_CLASS_UNSAFE = defineClassUnsafe;
            UNSAFE = unsafe;
            THROWABLE = throwable;
        }

        public static ProtectionDomain getProtectionDomain(final Class<?> source) {
            return source == null ? null //
                    : AccessController.doPrivileged((PrivilegedAction<ProtectionDomain>) () -> source.getProtectionDomain());
        }

        @SuppressWarnings("unchecked")
        public static <T> Class<T> defineClass(final byte[] b,
                                               final String className,
                                               final ClassLoader loader,
                                               final ProtectionDomain protection) throws Exception//
        {

            final ProtectionDomain protectionDomainToUse = protection == null ? PROTECTION_DOMAIN : protection;

            final Class<T> c;
            if (DEFINE_CLASS != null) {
                Object[] args = new Object[] { className, b, 0, Integer.valueOf(b.length), protectionDomainToUse };
                c = (Class<T>) DEFINE_CLASS.invoke(loader, args);
            }
            else if (DEFINE_CLASS_UNSAFE != null) {
                Object[] args = new Object[] { className, b, 0, Integer.valueOf(b.length), loader, protectionDomainToUse };
                c = (Class<T>) DEFINE_CLASS_UNSAFE.invoke(UNSAFE, args);
            }
            else {
                throw new InvokerCreateException(THROWABLE);
            }
            // Force static initializers to run.
            Class.forName(className, true, loader);
            return c;
        }

    }
}
