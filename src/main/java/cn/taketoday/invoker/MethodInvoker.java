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

import java.lang.reflect.Method;

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

}
