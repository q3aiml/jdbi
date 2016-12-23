/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.sqlobject.statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.jdbi.v3.core.extension.HandleSupplier;
import org.jdbi.v3.core.generic.GenericTypes;
import org.jdbi.v3.core.statement.Call;
import org.jdbi.v3.core.statement.OutParameters;
import org.jdbi.v3.sqlobject.Handler;
import org.jdbi.v3.sqlobject.HandlerFactory;
import org.jdbi.v3.sqlobject.SqlMethodAnnotation;
import org.jdbi.v3.sqlobject.SqlObjects;

/**
 * Support for stored proc invocation. Return value must be either null or OutParameters at present.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@SqlMethodAnnotation(SqlCall.Factory.class)
public @interface SqlCall {
    String value() default "";

    class Factory implements HandlerFactory {
        @Override
        public Handler buildHandler(Class<?> sqlObjectType, Method method) {
            return new CallHandler(sqlObjectType, method);
        }
    }

    class CallHandler extends CustomizingStatementHandler {
        private final Class<?> sqlObjectType;
        private final boolean returnOutParams;

        CallHandler(Class<?> sqlObjectType, Method method) {
            super(sqlObjectType, method);
            this.sqlObjectType = sqlObjectType;

            Type returnType = GenericTypes.resolveType(method.getGenericReturnType(), sqlObjectType);
            Class<?> returnClass = GenericTypes.getErasedType(returnType);
            if (Void.TYPE.equals(returnClass)) {
                returnOutParams = false;
            } else if (OutParameters.class.isAssignableFrom(returnClass)) {
                returnOutParams = true;
            } else {
                throw new IllegalArgumentException("@SqlCall methods may only return null or OutParameters at present");
            }
        }

        @Override
        public Object invoke(Object target, Object[] args, HandleSupplier handle) {
            String sql = handle.getConfig(SqlObjects.class).getSqlLocator().locate(sqlObjectType, getMethod());
            Call call = handle.getHandle().createCall(sql);
            applyCustomizers(call, args);

            OutParameters ou = call.invoke();

            if (returnOutParams) {
                return ou;
            } else {
                return null;
            }
        }
    }
}