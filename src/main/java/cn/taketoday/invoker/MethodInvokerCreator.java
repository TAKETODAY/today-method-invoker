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

import static cn.taketoday.context.Constant.SOURCE_FILE;
import static cn.taketoday.context.asm.Opcodes.ACC_FINAL;
import static cn.taketoday.context.asm.Opcodes.ACC_PUBLIC;
import static cn.taketoday.context.asm.Opcodes.JAVA_VERSION;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import cn.taketoday.context.Constant;
import cn.taketoday.context.asm.ClassVisitor;
import cn.taketoday.context.asm.Type;
import cn.taketoday.context.cglib.core.ClassEmitter;
import cn.taketoday.context.cglib.core.ClassGenerator;
import cn.taketoday.context.cglib.core.CodeEmitter;
import cn.taketoday.context.cglib.core.CodeGenerationException;
import cn.taketoday.context.cglib.core.DefaultGeneratorStrategy;
import cn.taketoday.context.cglib.core.EmitUtils;
import cn.taketoday.context.cglib.core.GeneratorStrategy;
import cn.taketoday.context.cglib.core.MethodInfo;
import cn.taketoday.context.cglib.core.ReflectUtils;
import cn.taketoday.context.cglib.core.TypeUtils;

/**
 * @author TODAY <br>
 *         2019-10-18 22:36
 */
public abstract class MethodInvokerCreator {

    public static MethodInvoker create(final Class<?> beanClass,
                                       final String name, final Class<?>... parameterClasses) throws NoSuchMethodException {

        final Method targetMethod = beanClass.getDeclaredMethod(name, parameterClasses);

        return new MethodInvokerGenerator(targetMethod, beanClass).create();
    }

    public static MethodInvoker create(Method mapping) {
        return new MethodInvokerGenerator(mapping).create();
    }

    // MethodInvoker object generator
    // --------------------------------------------------------------

    public static class MethodInvokerGenerator implements ClassGenerator {

        private Class<?> targetClass;
        private Method targetMethod;

        private String className;

        private GeneratorStrategy strategy = DefaultGeneratorStrategy.INSTANCE;

        public MethodInvokerGenerator(Method method) {
            this.targetMethod = method;
            this.targetClass = method.getDeclaringClass();
        }

        public MethodInvokerGenerator(Method method, Class<?> targetClass) {
            this.targetMethod = method;
            this.targetClass = targetClass;
        }

        protected ProtectionDomain getProtectionDomain() {
            return ReflectUtils.getProtectionDomain(targetClass);
        }

        public MethodInvoker create() {
            final Class<MethodInvoker> generateClass = generateClass();
            return ReflectUtils.newInstance(generateClass);
        }

        protected Class<MethodInvoker> generateClass() {

            try {

                final ClassLoader classLoader = targetClass.getClassLoader();
                if (classLoader == null) {
                    throw new IllegalStateException("ClassLoader is null while trying to define class " + getClassName()
                            + ". It seems that the loader has been expired from a weak reference somehow. "
                            + "Please file an issue at cglib's issue tracker.");
                }

                final byte[] b = strategy.generate(this);
                final ProtectionDomain protectionDomain = getProtectionDomain();
                if (protectionDomain == null) {
                    return ReflectUtils.defineClass(getClassName(), b, classLoader);
                }
                return ReflectUtils.defineClass(getClassName(), b, classLoader, protectionDomain);
            }
            catch (RuntimeException | Error e) {
                throw e;
            }
            catch (Exception e) {
                throw new CodeGenerationException(e);
            }
        }

        @Override
        public void generateClass(ClassVisitor v) throws NoSuchMethodException {

            final ClassEmitter ce = new ClassEmitter(v);

            ce.beginClass(JAVA_VERSION, ACC_PUBLIC | ACC_FINAL, //
                          getClassName(), MethodInvoker.class, SOURCE_FILE, Invoker.class);

            final Method invoke = MethodInvoker.class.getDeclaredMethod("invoke", Object.class, Object[].class);
            final MethodInfo invokeInfo = ReflectUtils.getMethodInfo(invoke);

            EmitUtils.nullConstructor(ce);

            final CodeEmitter codeEmitter = EmitUtils.beginMethod(ce, invokeInfo, ACC_PUBLIC | ACC_FINAL);

            if (!Modifier.isStatic(targetMethod.getModifiers())) {

                codeEmitter.visitVarInsn(Constant.ALOAD, 1);
                codeEmitter.checkcast(Type.getType(targetClass));
                // codeEmitter.dup();
            }

            if (targetMethod.getParameterCount() != 0) {
                final Class<?>[] parameterTypes = targetMethod.getParameterTypes();
                for (int i = 0; i < parameterTypes.length; i++) {
                    codeEmitter.visitVarInsn(Constant.ALOAD, 2);
                    codeEmitter.aaload(i);

                    Class<?> parameterClass = parameterTypes[i];
                    final Type parameterType = Type.getType(parameterClass);
                    if (parameterClass.isPrimitive()) {
                        final Type boxedType = TypeUtils.getBoxedType(parameterType); // java.lang.Long ...

                        codeEmitter.checkcast(boxedType);
                        final String name = parameterClass.getName() + "Value";
                        final String descriptor = "()" + parameterType.getDescriptor();

                        codeEmitter.visitMethodInsn(Constant.INVOKEVIRTUAL, boxedType.getInternalName(), name, descriptor, false);
                    }
                    else {
                        codeEmitter.checkcast(parameterType);
                    }
                }
            }

            final MethodInfo methodInfo = ReflectUtils.getMethodInfo(targetMethod);
            codeEmitter.invoke(methodInfo);

            if (targetMethod.getReturnType() == void.class) {
                codeEmitter.aconst_null();
            }

            codeEmitter.return_value();
            codeEmitter.end_method();

            ce.endClass();
        }

        protected String getClassName() {
            if (className == null) {
                this.className = new StringBuilder(100).append(targetClass.getName())
                        .append('$')
                        .append(System.nanoTime())
                        .append('$')
                        .append(targetMethod.getName()).toString();
            }
            return className;
        }
    }

}
