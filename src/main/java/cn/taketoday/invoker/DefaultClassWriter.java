/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2020 All Rights Reserved.
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * @author TODAY <br>
 *         2019-10-19 15:44
 */
public class DefaultClassWriter extends ClassWriter {

    public static final String DEBUG_LOCATION_PROPERTY = "cglib.debugLocation";

    private final String className;
    private static String debugLocation;
    private static Constructor<?> traceCtor;

    static {

        debugLocation = System.getProperty(DEBUG_LOCATION_PROPERTY);
        if (debugLocation != null) {
            System.err.println("CGLIB debugging enabled, writing to '" + debugLocation + "'");
            try {
                Class<?> clazz = Class.forName("org.objectweb.asm.util.TraceClassVisitor");
                traceCtor = clazz.getConstructor(ClassVisitor.class, PrintWriter.class);
            }
            catch (Throwable ignore) {}
        }
    }

    public DefaultClassWriter(String className) {
        super(ClassWriter.COMPUTE_FRAMES);
        this.className = className;
    }

    @Override
    public byte[] toByteArray() throws InvokerCreateException {

        return AccessController.doPrivileged((PrivilegedAction<byte[]>) () -> {

            final byte[] ret = super.toByteArray();

            final String debugLocation = DefaultClassWriter.debugLocation;

            if (debugLocation != null) {
                final String dirs = className.replace('.', File.separatorChar);

                try {
                    final String path = new StringBuilder()
                            .append(debugLocation)
                            .append(File.separatorChar)
                            .append(dirs).toString();

                    new File(path).getParentFile().mkdirs();

                    File file = new File(new File(debugLocation), dirs.concat(".class"));
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                    try {
                        out.write(ret);
                    }
                    finally {
                        out.close();
                    }

                    if (traceCtor != null) {
                        file = new File(new File(debugLocation), dirs.concat(".asm"));
                        out = new BufferedOutputStream(new FileOutputStream(file));
                        try {
                            ClassReader cr = new ClassReader(ret);
                            PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
                            ClassVisitor tcv = (ClassVisitor) traceCtor.newInstance(null, pw);

                            cr.accept(tcv, 0);
                            pw.flush();
                        }
                        finally {
                            out.close();
                        }
                    }
                }
                catch (Exception e) {
                    throw new InvokerCreateException(e);
                }
            }
            return ret;
        });

    }
}
