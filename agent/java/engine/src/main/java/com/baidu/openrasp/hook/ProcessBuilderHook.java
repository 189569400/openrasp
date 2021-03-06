/*
 * Copyright 2017-2019 Baidu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.openrasp.hook;

import com.baidu.openrasp.HookHandler;
import com.baidu.openrasp.cloud.model.ErrorType;
import com.baidu.openrasp.cloud.utils.CloudUtils;
import com.baidu.openrasp.config.Config;
import com.baidu.openrasp.plugin.checker.CheckParameter;
import com.baidu.openrasp.plugin.js.engine.JSContext;
import com.baidu.openrasp.plugin.js.engine.JSContextFactory;
import com.baidu.openrasp.tool.OSUtil;
import com.baidu.openrasp.tool.StackTrace;
import com.baidu.openrasp.tool.annotation.HookAnnotation;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by zhuming01 on 5/17/17.
 * All rights reserved
 */
@HookAnnotation
public class ProcessBuilderHook extends AbstractClassHook {

    /**
     * (none-javadoc)
     *
     * @see com.baidu.openrasp.hook.AbstractClassHook#getType()
     */
    @Override
    public String getType() {
        return "command";
    }

    /**
     * (none-javadoc)
     *
     * @see com.baidu.openrasp.hook.AbstractClassHook#isClassMatched(String)
     */
    @Override
    public boolean isClassMatched(String className) {
        if (getJdkVersion()) {
            return "java/lang/ProcessImpl".equals(className);
        } else {
            if (OSUtil.isLinux() || OSUtil.isMacOS()) {
                return "java/lang/UNIXProcess".equals(className);
            } else if (OSUtil.isWindows()) {
                return "java/lang/ProcessImpl".equals(className);
            }
            return false;
        }
    }

    /**
     * (none-javadoc)
     *
     * @see com.baidu.openrasp.hook.AbstractClassHook#hookMethod(CtClass)
     */
    @Override
    protected void hookMethod(CtClass ctClass) throws IOException, CannotCompileException, NotFoundException {
        if (ctClass.getName().contains("ProcessImpl")) {
            if (getJdkVersion()) {
                String src = getInvokeStaticSrc(ProcessBuilderHook.class, "checkCommand",
                        "$1,$2", byte[].class, byte[].class);
                insertBefore(ctClass, "<init>", null, src);
            } else {
                String src = getInvokeStaticSrc(ProcessBuilderHook.class, "checkCommand",
                        "$1", String[].class);
                insertBefore(ctClass, "<init>", null, src);
            }
        } else if (ctClass.getName().contains("UNIXProcess")) {
            String src = getInvokeStaticSrc(ProcessBuilderHook.class, "checkCommand",
                    "$1,$2", byte[].class, byte[].class);
            insertBefore(ctClass, "<init>", null, src);
        }
    }

    public static void checkCommand(byte[] command, byte[] args) {
        LinkedList<String> commands = new LinkedList<String>();
        if (command != null && command.length > 0) {
            commands.add(new String(command, 0, command.length - 1));
        }
        if (args != null && args.length > 0) {
            int position = 0;
            for (int i = 0; i < args.length; i++) {
                if (args[i] == 0) {
                    commands.add(new String(Arrays.copyOfRange(args, position, i)));
                    position = i + 1;
                }
            }
        }
        checkCommand(commands);
    }

    public static void checkCommand(String[] commnad) {
        LinkedList<String> commands = new LinkedList<String>();
        Collections.addAll(commands, commnad);
        checkCommand(commands);
    }

    /**
     * ????????????hook???
     *
     * @param command ????????????
     */
    public static void checkCommand(List<String> command) {
        if (command != null && !command.isEmpty()) {
            Scriptable params = null;
            try {
                JSContext cx = JSContextFactory.enterAndInitContext();
                params = cx.newObject(cx.getScope());
                params.put("command", params, StringUtils.join(command, " "));
                List<String> stackInfo = StackTrace.getStackTraceArray(Config.REFLECTION_STACK_START_INDEX,
                        Config.getConfig().getPluginMaxStack());
                Scriptable stackArray = cx.newArray(cx.getScope(), stackInfo.toArray());
                params.put("stack", params, stackArray);
            } catch (Throwable t) {
                String message = t.getMessage();
                int errorCode = ErrorType.HOOK_ERROR.getCode();
                HookHandler.LOGGER.warn(CloudUtils.getExceptionObject(message, errorCode), t);
            }
            if (params != null) {
                HookHandler.doCheckWithoutRequest(CheckParameter.Type.COMMAND, params);
            }
        }
    }

    /**
     * ??????jdk?????????????????????8
     */
    private boolean getJdkVersion() {
        String javaVersion = System.getProperty("java.version");
        return javaVersion.startsWith("1.9") || javaVersion.startsWith("10.") || javaVersion.startsWith("11.");
    }
}
