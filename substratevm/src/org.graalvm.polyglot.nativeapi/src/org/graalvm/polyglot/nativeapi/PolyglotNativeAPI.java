/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.polyglot.nativeapi;

import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_array_expected;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_generic_failure;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_number_expected;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_ok;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_pending_exception;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.Threading;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.nativeapi.types.CBoolPointer;
import org.graalvm.polyglot.nativeapi.types.CInt16Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt32Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt64Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt64PointerPointer;
import org.graalvm.polyglot.nativeapi.types.CInt8Pointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedBytePointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedIntPointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedShortPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallback;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallbackInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContext;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextBuilder;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextBuilderPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngine;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngineBuilder;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngineBuilderPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEnginePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExceptionHandle;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExceptionHandlePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExtendedErrorInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExtendedErrorInfoPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolateThread;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotLanguage;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotLanguagePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValue;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValuePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.SizeTPointer;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CConst;
import com.oracle.svm.core.c.CHeader;
import com.oracle.svm.core.c.CUnsigned;
import com.oracle.svm.core.handles.ObjectHandlesImpl;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.jvmstat.PerfDataSupport;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

// Checkstyle: stop method name check

@SuppressWarnings("unused")
@CHeader(value = PolyglotAPIHeader.class)
public final class PolyglotNativeAPI {

    private static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;

    private static final int MAX_UNSIGNED_BYTE = (1 << 8) - 1;
    private static final int MAX_UNSIGNED_SHORT = (1 << 16) - 1;
    private static final long MAX_UNSIGNED_INT = (1L << 32) - 1;
    private static final UnsignedWord POLY_AUTO_LENGTH = WordFactory.unsigned(0xFFFFFFFFFFFFFFFFL);
    private static final int DEFAULT_FRAME_CAPACITY = 16;

    private static final ThreadLocal<CallbackException> exceptionsTL = new ThreadLocal<>();
    private static final FastThreadLocalObject<ThreadLocalState> threadLocals = FastThreadLocalFactory.createObject(ThreadLocalState.class, "PolyglotNativeAPI.threadLocals");

    private static ThreadLocalState ensureLocalsInitialized() {
        ThreadLocalState state = threadLocals.get();
        if (state == null) {
            state = new ThreadLocalState();
            threadLocals.set(state);
        }
        return state;
    }

    private static ThreadLocalHandles<PolyglotNativeAPITypes.PolyglotHandle> getHandles() {
        ThreadLocalState locals = ensureLocalsInitialized();
        if (locals.handles == null) {
            locals.handles = new ThreadLocalHandles<>(DEFAULT_FRAME_CAPACITY);
        }
        return locals.handles;
    }

    private static final ObjectHandlesImpl objectHandles = new ObjectHandlesImpl(
                    WordFactory.signed(Long.MIN_VALUE), ThreadLocalHandles.nullHandle().subtract(1), ThreadLocalHandles.nullHandle());

    private static final class ThreadLocalState {
        ThreadLocalHandles<PolyglotNativeAPITypes.PolyglotHandle> handles;

        Throwable lastException;
        PolyglotExtendedErrorInfo lastExceptionUnmanagedInfo;

        PolyglotException polyglotException;

        PolyglotStatus lastErrorCode = poly_ok;
    }

    private static final ExecutorService closeContextExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5L, TimeUnit.SECONDS, new SynchronousQueue<>());

    @CEntryPoint(name = "poly_create_engine_builder", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a new context builder that allows to configure an engine instance.",
                    "",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_engine_builder(PolyglotIsolateThread thread, PolyglotEngineBuilderPointer result) {
        resetErrorState();
        ObjectHandle handle = createHandle(Engine.newBuilder());
        result.write(handle);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_builder_option", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets an option for an <code>poly_engine_builder</code> that will apply to constructed engines.",
                    "<p>",
                    "",
                    " @param engine_builder that is assigned an option.",
                    " @param key_utf8 0 terminated and UTF-8 encoded key for the option.",
                    " @param value_utf8 0 terminated and UTF-8 encoded value for the option.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_engine_builder(PolyglotIsolateThread thread, PolyglotEngineBuilder engine_builder, @CConst CCharPointer key_utf8, @CConst CCharPointer value_utf8) {
        resetErrorState();
        Engine.Builder eb = fetchHandle(engine_builder);
        eb.option(CTypeConversion.toJavaString(key_utf8), CTypeConversion.toJavaString(value_utf8));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_builder_build", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Builds an <code>engine</code> from an <code>engine_builder</code>. The same builder can be used to ",
                    "produce multiple <code>poly_engine</code> instances.",
                    "",
                    " @param engine_builder that is used to build.",
                    " @param result the created engine.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_engine_builder_build(PolyglotIsolateThread thread, PolyglotEngineBuilder engine_builder, PolyglotEnginePointer result) {
        resetErrorState();
        Engine.Builder engineBuilder = fetchHandle(engine_builder);
        result.write(createHandle(engineBuilder.build()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_engine", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot engine: An execution engine for Graal guest languages that allows to inspect the ",
                    "installed languages and can have multiple execution contexts.",
                    "",
                    "Engine is a unit that holds configuration, instruments, and compiled code for all contexts assigned ",
                    "to this engine.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_engine(PolyglotIsolateThread thread, PolyglotEnginePointer result) {
        resetErrorState();
        PolyglotNativeAPITypes.PolyglotHandle handle = createHandle(Engine.create());
        result.write(handle);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_close", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Closes this engine and frees up allocated native resources. If there are still open context",
                    "instances that were created using this engine and they are currently not being executed then",
                    "they will be closed automatically. If an attempt to close an engine was successful then",
                    "consecutive calls to close have no effect. If a context is cancelled then the currently",
                    "executing thread will throw a {@link PolyglotException}.",
                    "",
                    " @param engine to be closed.",
                    " @param cancel_if_executing if <code>true</code> then currently executing contexts will be cancelled.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_engine_close(PolyglotIsolateThread thread, PolyglotEngine engine, boolean cancel_if_executing) {
        resetErrorState();
        Engine jEngine = fetchHandle(engine);
        jEngine.close(cancel_if_executing);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_get_languages", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns an array where each element is a <code>poly_language<code> handle.",
                    "",
                    "To use, make two calls to this method:",
                    "<p>",
                    "   The first passing a size_t pointer to write the count of the languages to (IE: 'size_t poly_language_count')",
                    "   The second passing the value of the written size_t length, and a pointer to a poly_language[poly_language_size]",
                    "<code>",
                    "size_t poly_language_count;",
                    "",
                    "// Get the number of languages",
                    "poly_engine_get_languages(thread, engine, NULL, &poly_language_count);",
                    "",
                    "// Allocate properly sized array of poly_language[]",
                    "poly_language languages_ptr[poly_language_count];",
                    "",
                    "// Write the language handles into the array",
                    "poly_engine_get_languages(thread, engine, &languages_ptr, &num_languages);",
                    "</code>",
                    " @param engine for which languages are returned.",
                    " @param language_array array to write <code>poly_language</code>s to or NULL.",
                    " @param size the number of languages in the engine.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_engine_get_languages(PolyglotIsolateThread thread, PolyglotEngine engine, PolyglotLanguagePointer language_array, SizeTPointer size) {
        resetErrorState();
        Engine jEngine = fetchHandle(engine);
        UnsignedWord languagesSize = WordFactory.unsigned(jEngine.getLanguages().size());
        if (language_array.isNull()) {
            size.write(languagesSize);
        } else {
            size.write(languagesSize);
            List<Language> sortedLanguages = sortedLangs(fetchHandle(engine));
            for (int i = 0; i < sortedLanguages.size(); i++) {
                language_array.write(i, createHandle(sortedLanguages.get(i)));
            }
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_context_builder", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a context with a new engine polyglot engine with a list ",
                    "",
                    "A context holds all of the program data. Each context is by default isolated from all other contexts",
                    "with respect to program data and evaluation semantics.",
                    "",
                    " @param permittedLanguages array of 0 terminated language identifiers in UTF-8 that are permitted.",
                    " @param length of the array of language identifiers.",
                    " @param result the created context.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_context_builder(PolyglotIsolateThread thread, @CConst CCharPointerPointer permitted_languages, UnsignedWord length, PolyglotContextBuilderPointer result) {
        resetErrorState();
        List<String> jPermittedLangs = new ArrayList<>();
        for (int i = 0; length.aboveThan(i); i++) {
            jPermittedLangs.add(CTypeConversion.toJavaString(permitted_languages.read(i)));
        }
        Context.Builder c = Context.newBuilder(jPermittedLangs.toArray(new String[jPermittedLangs.size()]));
        result.write(createHandle(c));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_engine", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets an engine for the context builder.",
                    "",
                    " @param context_builder that is assigned an engine.",
                    " @param engine to assign to this builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_engine(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, PolyglotEngine engine) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        Engine jEngine = fetchHandle(engine);
        contextBuilder.engine(jEngine);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_option", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets an option on a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is assigned an option.",
                    " @param key_utf8 0 terminated and UTF-8 encoded key for the option.",
                    " @param value_utf8 0 terminated and UTF-8 encoded value for the option.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_option(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, @CConst CCharPointer key_utf8, @CConst CCharPointer value_utf8) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.option(CTypeConversion.utf8ToJavaString(key_utf8), CTypeConversion.utf8ToJavaString(value_utf8));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_all_access", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows all access for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_all_access bool value that defines all access.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_all_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_all_access) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowAllAccess(allow_all_access);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_io", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows IO for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_IO bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_io(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_IO) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowIO(allow_IO ? IOAccess.ALL : IOAccess.NONE);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_native_access", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows native access for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_native_access bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_native_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_native_access) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowNativeAccess(allow_native_access);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_polyglot_access", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows polyglot access for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_polyglot_access bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_polyglot_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_polyglot_access) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowPolyglotAccess(allow_polyglot_access ? PolyglotAccess.ALL : PolyglotAccess.NONE);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_create_thread", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows thread creation for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_create_thread bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_create_thread(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_create_thread) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowCreateThread(allow_create_thread);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_experimental_options", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows experimental options for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_experimental_options bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_experimental_options(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_experimental_options) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowExperimentalOptions(allow_experimental_options);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_build", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Builds a <code>context</code> from a <code>context_builder</code>. The same builder can be used to ",
                    "produce multiple <code>poly_context</code> instances.",
                    "",
                    " @param context_builder that is used to construct a new context.",
                    " @param result the created context.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_builder_build(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, PolyglotContextPointer result) {
        resetErrorState();
        Context.Builder contextBuilder = fetchHandle(context_builder);
        result.write(createHandle(contextBuilder.build()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_context", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a context with default configuration.",
                    "",
                    "A context holds all of the program data. Each context is by default isolated from all other contexts",
                    "with respect to program data and evaluation semantics.",
                    "",
                    " @param permitted_languages array of 0 terminated language identifiers in UTF-8 that are permitted, or NULL for ",
                    "        supporting all available languages.",
                    " @param length of the array of language identifiers.",
                    " @param result the created context.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_context(PolyglotIsolateThread thread, @CConst CCharPointerPointer permitted_languages, UnsignedWord length, PolyglotContextPointer result) {
        resetErrorState();
        Context c;
        if (permitted_languages.isNull()) {
            c = Context.create();
        } else {
            List<String> jPermittedLangs = new ArrayList<>();
            for (int i = 0; length.aboveThan(i); i++) {
                jPermittedLangs.add(CTypeConversion.toJavaString(permitted_languages.read(i)));
            }
            c = Context.create(jPermittedLangs.toArray(new String[jPermittedLangs.size()]));
        }
        result.write(createHandle(c));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_close", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Closes this context and frees up potentially allocated native resources. A ",
                    "context cannot free all native resources allocated automatically. For this reason",
                    "it is necessary to close contexts after use. If a context is canceled then the",
                    "currently executing thread will throw a {@link PolyglotException}. Please note ",
                    "that canceling a single context can negatively affect the performance of other ",
                    "executing contexts constructed with the same engine.",
                    "<p>",
                    "If internal errors occur during closing of the language then they are printed to the ",
                    "configured {@link Builder#err(OutputStream) error output stream}. If a context was ",
                    "closed then all its methods will throw an {@link IllegalStateException} when invoked. ",
                    "If an attempt to close a context was successful then consecutive calls to close have ",
                    "no effect.",
                    "",
                    " @param context to be closed.",
                    " @param cancel_if_executing if <code>true</code> then currently executing context will be cancelled.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_close(PolyglotIsolateThread thread, PolyglotContext context, boolean cancel_if_executing) {
        resetErrorState();
        Context jContext = fetchHandle(context);
        jContext.close(cancel_if_executing);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_close_async", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Request for this context to be closed asynchronously.",
                    "An attempt to closed this context will later be made via a background thread.",
                    "Note this call will attempt to close a context; however, it is not guaranteed the closure will be successful.",
                    "",
                    " @param context to be closed.",
                    " @return poly_ok if closure request submitted, poly_generic_error if there is a failure.",
                    " @since 22.3",
    })
    public static PolyglotStatus poly_context_close_async(PolyglotIsolateThread thread, PolyglotContext context) {
        resetErrorState();
        Context jContext = fetchHandle(context);
        closeContextExecutor.execute(() -> jContext.close(true));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_eval", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Evaluate a source of guest languages inside a context.",
                    "",
                    " @param context in which we evaluate source code.",
                    " @param language_id the language identifier.",
                    " @param name_utf8 given to the evaluate source code.",
                    " @param source_utf8 the source code to be evaluated.",
                    " @param result <code>poly_value</code> that is the result of the evaluation. You can pass <code>NULL</code> if you just want to evaluate the source and you can ignore the result.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @see org::graalvm::polyglot::Context::eval",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_eval(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language_id, @CConst CCharPointer name_utf8,
                    @CConst CCharPointer source_utf8, PolyglotValuePointer result) throws Exception {
        resetErrorState();
        Context c = fetchHandle(context);
        String languageName = CTypeConversion.toJavaString(language_id);
        String jName = CTypeConversion.utf8ToJavaString(name_utf8);
        String jCode = CTypeConversion.utf8ToJavaString(source_utf8);

        Source sourceCode = Source.newBuilder(languageName, jCode, jName).build();
        Value evalResult = c.eval(sourceCode);
        if (result.isNonNull()) {
            result.write(createHandle(evalResult));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_get_engine", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns the engine this context belongs to.",
                    "",
                    " @param context for which we extract the bindings.",
                    " @param result a value whose members correspond to the symbols in the top scope of the `language_id`.",
                    " @return poly_ok if everything is fine, poly_generic_failure if there is an error.",
                    " @see org::graalvm::polyglot::Context::getEngine",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_get_engine(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        Context jContext = fetchHandle(context);
        result.write(createHandle(jContext.getEngine()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_get_bindings", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a value that represents the top-most bindings of a language. The top-most bindings of",
                    "the language are a value whose members correspond to each symbol in the top scope.",
                    "",
                    "Languages may allow modifications of members of the returned bindings object at the",
                    "language's discretion. If the language was not yet initialized it",
                    "will be initialized when the bindings are requested.",
                    "",
                    " @param context for which we extract the bindings.",
                    " @param language_id the language identifier.",
                    " @param result a value whose members correspond to the symbols in the top scope of the `language_id`.",
                    " @return poly_generic_failure if the language does not exist, if context is already closed, ",
                    "        in case the lazy initialization failed due to a guest language error.",
                    " @see org::graalvm::polyglot::Context::getBindings",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_get_bindings(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language_id, PolyglotValuePointer result) {
        resetErrorState();
        Context jContext = fetchHandle(context);
        String jLanguage = CTypeConversion.toJavaString(language_id);
        Value languageBindings = jContext.getBindings(jLanguage);
        result.write(createHandle(languageBindings));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_get_polyglot_bindings", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns polyglot bindings that may be used to exchange symbols between the host and ",
                    "guest languages. All languages have unrestricted access to the polyglot bindings. ",
                    "The returned bindings object always has members and its members are readable, writable and removable.",
                    "",
                    "Guest languages may put and get members through language specific APIs. For example, ",
                    "in JavaScript symbols of the polyglot bindings can be accessed using ",
                    "`Polyglot.import(\"name\")` and set using `Polyglot.export(\"name\", value)`. Please see ",
                    "the individual language reference on how to access these symbols.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is already closed.",
                    " @see org::graalvm::polyglot::Context::getPolyglotBindings",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_context_get_polyglot_bindings(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        Context jContext = fetchHandle(context);
        result.write(createHandle(jContext.getPolyglotBindings()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_can_execute", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks whether a polyglot value can be executed.",
                    "",
                    " @param value a polyglot value.",
                    " @param result true if the value can be executed, false otherwise.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @see org::graalvm::polyglot::Value::canExecute",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_can_execute(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.canExecute()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_execute", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Executes a value if it can be executed and returns its result. All arguments passed ",
                    "must be polyglot values.",
                    "",
                    " @param value to be executed.",
                    " @param args array of poly_value.",
                    " @param args_size length of the args array.",
                    " @param result <code>poly_value</code> that is the result of the execution. You can pass <code>NULL</code> if you just want to execute the source and you can ignore the result.",
                    " @return poly_ok if all works, poly_generic_error if the underlying context was closed, if a wrong ",
                    "         number of arguments was provided or one of the arguments was not applicable, if this value cannot be executed,",
                    " and if a guest language error occurred during execution.",
                    " @see org::graalvm::polyglot::Value::execute",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_execute(PolyglotIsolateThread thread, PolyglotValue value, PolyglotValuePointer args, int args_size, PolyglotValuePointer result) {
        resetErrorState();
        Value function = fetchHandle(value);
        Object[] jArgs = new Object[args_size];
        for (int i = 0; i < args_size; i++) {
            PolyglotValue handle = args.read(i);
            jArgs[i] = fetchHandle(handle);
        }

        Value resultValue = function.execute(jArgs);
        if (result.isNonNull()) {
            result.write(createHandle(resultValue));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_get_member", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns the member with a given `utf8_identifier` or `null` if the member does not exist.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the value has no members, the given identifier exists ",
                    "        but is not readable, if a guest language error occurred during execution.",
                    " @see org::graalvm::polyglot::Value::getMember",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_get_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer utf8_identifier, PolyglotValuePointer result) {
        resetErrorState();
        Value jObject = fetchHandle(value);
        result.write(createHandle(jObject.getMember(CTypeConversion.utf8ToJavaString(utf8_identifier))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_put_member", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets the value of a member with the `utf8_identifier`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the context is already closed, if the value does ",
                    "         not have any members, the key does not exist and new members cannot be added, or the existing ",
                    "         member is not modifiable.",
                    " @see org::graalvm::polyglot::Value::putMember",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_put_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer utf8_identifier, PolyglotValue member) {
        resetErrorState();
        Value jObject = fetchHandle(value);
        Value jMember = fetchHandle(member);
        jObject.putMember(CTypeConversion.utf8ToJavaString(utf8_identifier), jMember);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_has_member", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if such a member exists for the given `utf8_identifier`. If the value has no members ",
                    "then it returns `false`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "         during execution.",
                    " @see org::graalvm::polyglot::Value::putMember",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_has_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer utf8_identifier, CBoolPointer result) {
        resetErrorState();
        Value jObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jObject.hasMember(CTypeConversion.utf8ToJavaString(utf8_identifier))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_boolean", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot boolean value from a C boolean.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_boolean(PolyglotIsolateThread thread, PolyglotContext context, boolean value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(value)));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_int8(PolyglotIsolateThread thread, PolyglotContext context, byte value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Byte.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_int16(PolyglotIsolateThread thread, PolyglotContext context, short value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Short.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_int32(PolyglotIsolateThread thread, PolyglotContext context, int value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Integer.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int64", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int64_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_int64(PolyglotIsolateThread thread, PolyglotContext context, long value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Long.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `uint8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_uint8(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned byte value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Byte.toUnsignedInt(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `uint16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_uint16(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned short value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Short.toUnsignedInt(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `uint32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_uint32(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned int value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Integer.toUnsignedLong(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_float", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot floating point number from C `float`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_float(PolyglotIsolateThread thread, PolyglotContext context, float value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Float.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_double", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot floating point number from C `double`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_double(PolyglotIsolateThread thread, PolyglotContext context, double value, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Double.valueOf(value))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_character", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot character from C `char`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_character(PolyglotIsolateThread thread, PolyglotContext context, char character, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(character)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_string_utf8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot string from an UTF-8 encoded string. Only the `length` of the string in bytes is used unless",
                    "`POLY_AUTO_LENGTH` is passed as the `length` argument.",
                    "",
                    " @param string the C string, null terminated or not.",
                    " @param length the length of C string, or POLY_AUTO_LENGTH if the string is null terminated.",
                    " @return the polyglot string value.",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_string_utf8(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer string, UnsignedWord length, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(length.equal(POLY_AUTO_LENGTH) ? CTypeConversion.toJavaString(string) : CTypeConversion.toJavaString(string, length, UTF8_CHARSET))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_null", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates the polyglot `null` value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_null(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(null)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_object", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot object with no members.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::ProxyObject::fromMap",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_object(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        Context c = fetchHandle(context);
        ProxyObject proxy = ProxyObject.fromMap(new HashMap<>());
        result.write(createHandle(c.asValue(proxy)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_array", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot array from the C array of polyglot values.",
                    "",
                    " @param value_array array containing polyglot values",
                    " @param array_length the number of elements in the value_array",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed, ",
                    "         if the array does not contain polyglot values.",
                    " @see org::graalvm::polyglot::ProxyArray::fromList",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_array(PolyglotIsolateThread thread, PolyglotContext context, @CConst PolyglotValuePointer value_array, long array_length, PolyglotValuePointer result) {
        resetErrorState();
        Context ctx = fetchHandle(context);
        List<Object> values = new LinkedList<>();
        for (long i = 0; i < array_length; i++) {
            values.add(fetchHandle(value_array.read(i)));
        }
        result.write(createHandle(ctx.asValue(ProxyArray.fromList(values))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_has_array_elements", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Check whether a polyglot value has array elements. ",
                    "",
                    "If yes, array elements can be accessed using {@link poly_value_get_array_element}, ",
                    "{@link poly_value_set_array_element}, {@link poly_value_remove_array_element} and the array size ",
                    "can be queried using {@link poly_value_get_array_size}.",
                    "",
                    " @param value value that we are checking.",
                    " @return true if the value has array elements.",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::hasArrayElements",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_has_array_elements(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.hasArrayElements()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_get_array_element", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns an array element from the specified index. ",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    " @param value value that has array elements.",
                    " @param index index of the element starting from 0.",
                    " @return the array element.",
                    " @return poly_ok if all works, poly_generic_failure if the array index does not exist, if index is not readable, if the ",
                    "         underlying context was closed, if guest language error occurred during execution, poly_array_expected if the ",
                    "         value has no array elements.",
                    " @see org::graalvm::polyglot::Value::getArrayElement",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_get_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, PolyglotValuePointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        if (!jValue.hasArrayElements()) {
            throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
        }
        result.write(createHandle(jValue.getArrayElement(index)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_set_array_element", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets the value at a given index.",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    " @param value value that we are checking.",
                    " @param index index of the element starting from 0.",
                    " @param element to be written into the array.",
                    " @param result true if the value has array elements.",
                    " @return poly_ok if all works, poly_generic_failure if the array index does not exist, if index is not writeable, if the ",
                    "         underlying context was closed, if guest language error occurred during execution, poly_array_expected if the value has no array elements..",
                    " @see org::graalvm::polyglot::Value::setArrayElement",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_set_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, PolyglotValue element) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        if (!jValue.hasArrayElements()) {
            throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
        }
        Value jElement = fetchHandle(element);
        jValue.setArrayElement(index, jElement);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_remove_array_element", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Removes an array element at a given index.",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    " @param value value that we are checking.",
                    " @param index index of the element starting from 0.",
                    " @param result true if the underlying array element could be removed, otherwise false.",
                    " @return poly_ok if all works, poly_generic_failure if the array index does not exist, if index is not removable, if the ",
                    "         underlying context was closed, if guest language error occurred during execution, poly_array_expected if the ",
                    "         value has no array elements.",
                    " @see org::graalvm::polyglot::Value::removeArrayElement",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_remove_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        if (!jValue.hasArrayElements()) {
            throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
        }
        result.write(CTypeConversion.toCBoolean(jValue.removeArrayElement(index)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_get_array_size", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the size of the polyglot value that has array elements.",
                    "",
                    " @param value value that has array elements.",
                    " @param result number of elements in the value.",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "         during execution, poly_array_expected if the value has no array elements.",
                    " @see org::graalvm::polyglot::Value::removeArrayElement",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_get_array_size(PolyglotIsolateThread thread, PolyglotValue value, CInt64Pointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        if (!jValue.hasArrayElements()) {
            throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
        }
        result.write(jValue.getArraySize());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_null", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is `null` like.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::isNull",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_is_null(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isNull()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_boolean", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value represents a boolean value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted. ",
                    " @see org::graalvm::polyglot::Value::isBoolean",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_is_boolean(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isBoolean()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_string", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value represents a string.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::isString",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_is_string(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isString()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_number", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value represents a number.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::isNumber",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_is_number(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isNumber()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_float", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into a C float.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInFloat",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_fits_in_float(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInFloat()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_double", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into a C double.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInDouble",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_double(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInDouble()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInByte",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInByte()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int16(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInInt();
        if (jResult) {
            int intValue = jValue.asInt();
            jResult = intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInInt",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInInt()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int64", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int64_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInLong",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int64(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInLong()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_uint8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `uint8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInInt();
        if (jResult) {
            int intValue = jValue.asInt();
            jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_BYTE;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_uint16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `uint16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint16(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInInt();
        if (jResult) {
            int intValue = jValue.asInt();
            jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_SHORT;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_uint32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `uint32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInLong();
        if (jResult) {
            long intValue = jValue.asLong();
            jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_INT;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_string_utf8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Converts a string value to a C string by filling the <code>buffer</code> with with a string encoded in UTF-8 and ",
                    "storing the number of written bytes to <code>result</code>. If the the buffer is <code>NULL</code> writes the required",
                    "size to <code>result</code>.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if a guest language error occurred during execution ",
                    "         poly_string_expected if the value is not a string.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_string_utf8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        if (jValue.isString()) {
            writeString(jValue.asString(), buffer, buffer_size, result, UTF8_CHARSET);
        } else {
            throw reportError("Expected type String but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_string_expected);
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_to_string_utf8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a <code>toString</code> representation of a <code>poly_value</code> by filling the <code>buffer</code> with with a string encoded ",
                    "in UTF-8 and stores the number of written bytes to <code>result</code>. If the the buffer is <code>NULL</code> writes the ",
                    "required size to <code>result</code>.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if a guest language error occurred during execution ",
                    "         poly_string_expected if the value is not a string.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_to_string_utf8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        writeString(jValue.toString(), buffer, buffer_size, result, UTF8_CHARSET);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_boolean", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a boolean representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted. ",
                    " @see org::graalvm::polyglot::Value::asBoolean",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_bool(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        Value jValue = fetchHandle(value);
        if (jValue.isBoolean()) {
            result.write(CTypeConversion.toCBoolean(jValue.asBoolean()));
        } else {
            throw reportError("Expected type Boolean but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_boolean_expected);
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int8_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted. ",
                    " @see org::graalvm::polyglot::Value::asByte",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_int8(PolyglotIsolateThread thread, PolyglotValue value, CInt8Pointer result) {
        resetErrorState();
        Value valueObject = fetchHandle(value);
        result.write(valueObject.asByte());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int32_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_int16(PolyglotIsolateThread thread, PolyglotValue value, CInt16Pointer result) {
        resetErrorState();
        Value valueObject = fetchHandle(value);
        int intValue = valueObject.asInt();
        if (intValue < Short.MIN_VALUE || intValue > Short.MAX_VALUE) {
            throw reportError("Value " + intValue + " does not fit into int_16_t.", poly_generic_failure);
        }
        result.write((short) intValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int32_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_int32(PolyglotIsolateThread thread, PolyglotValue value, CInt32Pointer result) {
        resetErrorState();
        Value valueObject = fetchHandle(value);
        result.write(valueObject.asInt());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int64", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int64_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_int64(PolyglotIsolateThread thread, PolyglotValue value, CInt64Pointer result) {
        resetErrorState();
        Value valueObject = fetchHandle(value);
        result.write(valueObject.asLong());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_uint8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a uint8_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_uint8(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedBytePointer result) {
        resetErrorState();
        Value valueObject = fetchHandle(value);
        int intValue = valueObject.asInt();
        if (intValue < 0 || intValue > MAX_UNSIGNED_BYTE) {
            throw reportError("Value " + Integer.toUnsignedString(intValue) + "does not fit in uint8_t", poly_generic_failure);
        }
        result.write((byte) intValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_uint16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a uint16_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_uint16(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedShortPointer result) {
        resetErrorState();
        Value valueObject = fetchHandle(value);
        int intValue = valueObject.asInt();
        if (intValue < 0 || intValue > MAX_UNSIGNED_SHORT) {
            throw reportError("Value " + Integer.toUnsignedString(intValue) + "does not fit in uint16_t", poly_generic_failure);
        }
        result.write((short) intValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_uint32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a uint32_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asLong",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_uint32(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedIntPointer result) {
        resetErrorState();
        Value valueObject = fetchHandle(value);
        long longValue = valueObject.asLong();
        if (longValue < 0 || longValue > MAX_UNSIGNED_INT) {
            throw reportError("Value " + Long.toUnsignedString(longValue) + "does not fit in uint32_t", poly_generic_failure);
        }
        result.write((int) longValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_float", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a float representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asFloat",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_float(PolyglotIsolateThread thread, PolyglotValue value, CFloatPointer result) {
        resetErrorState();
        Value dataObject = fetchHandle(value);
        result.write(dataObject.asFloat());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_double", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a double representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is <code>null</code>, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asDouble",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_value_as_double(PolyglotIsolateThread thread, PolyglotValue value, CDoublePointer result) {
        resetErrorState();
        Value dataObject = fetchHandle(value);
        if (dataObject.isNumber()) {
            result.write(dataObject.asDouble());
        } else {
            throw reportError("Value is not a number.", poly_number_expected);
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_language_get_id", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the primary identification string of this language. The language id is",
                    "used as the primary way of identifying languages in the polyglot API. (eg. <code>js</code>)",
                    "",
                    " @return a language ID string.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_language_get_id(PolyglotIsolateThread thread, PolyglotLanguage language, CCharPointer utf8_result, UnsignedWord buffer_size, SizeTPointer length) {
        resetErrorState();
        Language jLanguage = fetchHandle(language);
        writeString(jLanguage.getId(), utf8_result, buffer_size, length, UTF8_CHARSET);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_get_last_error_info", exceptionHandler = GenericFailureExceptionHandler.class, documentation = {
                    "Returns information about last error that occurred on this thread in the poly_extended_error_info structure.",
                    "",
                    "This method must be called right after a failure occurs and can be called only once.",
                    "",
                    " @return information about the last failure on this thread.",
                    " @since 19.0",
    })
    @Uninterruptible(reason = "Prevent safepoint checks before pausing recurring callback.")
    public static PolyglotStatus poly_get_last_error_info(PolyglotIsolateThread thread, @CConst PolyglotExtendedErrorInfoPointer result) {
        ThreadingSupportImpl.pauseRecurringCallback("Prevent recurring callback from throwing another exception.");
        try {
            return doGetLastErrorInfo(result);
        } finally {
            ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
        }
    }

    @Uninterruptible(reason = "Not really, but our caller is.", calleeMustBe = false)
    private static PolyglotStatus doGetLastErrorInfo(PolyglotExtendedErrorInfoPointer result) {
        return doGetLastErrorInfo0(result);
    }

    private static PolyglotStatus doGetLastErrorInfo0(PolyglotExtendedErrorInfoPointer result) {
        ThreadLocalState state = threadLocals.get();
        if (state == null || state.lastException == null) {
            result.write(WordFactory.nullPointer());
            return poly_ok;
        }

        assert state.lastErrorCode != poly_ok;
        freeUnmanagedErrorState(state);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(state.lastException.getMessage());
        pw.println("The full stack trace is:");
        state.lastException.printStackTrace(pw);
        ByteBuffer message = UTF8_CHARSET.encode(sw.toString());

        int messageSize = message.capacity() + 1;
        CCharPointer messageChars = UnmanagedMemory.malloc(messageSize);
        ByteBuffer messageBuffer = CTypeConversion.asByteBuffer(messageChars, messageSize);
        messageBuffer.put(message).put((byte) 0);

        PolyglotExtendedErrorInfo info = UnmanagedMemory.malloc(SizeOf.get(PolyglotExtendedErrorInfo.class));
        info.setErrorCode(state.lastErrorCode.getCValue());
        info.setErrorMessage(messageChars);

        state.lastExceptionUnmanagedInfo = info;
        result.write(state.lastExceptionUnmanagedInfo);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_function", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot function that calls back into native code.",
                    "",
                    " @param data user defined data to be passed into the function.",
                    " @param callback function that is called from the polyglot engine.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @see org::graalvm::polyglot::ProxyExecutable",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_function(PolyglotIsolateThread thread, PolyglotContext context, PolyglotCallback callback, VoidPointer data,
                    PolyglotValuePointer value) {
        resetErrorState();
        Context c = fetchHandle(context);
        ProxyExecutable executable = (Value... arguments) -> {
            int frame = getHandles().pushFrame(DEFAULT_FRAME_CAPACITY);
            try {
                ObjectHandle[] handleArgs = new ObjectHandle[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    handleArgs[i] = createHandle(arguments[i]);
                }
                PolyglotCallbackInfo cbInfo = (PolyglotCallbackInfo) createHandle(new PolyglotCallbackInfoInternal(handleArgs, data));
                PolyglotValue result = callback.invoke((PolyglotIsolateThread) CurrentIsolate.getCurrentThread(), cbInfo);
                CallbackException ce = exceptionsTL.get();
                if (ce != null) {
                    exceptionsTL.remove();
                    throw ce;
                } else {
                    return PolyglotNativeAPI.fetchHandle(result);
                }
            } finally {
                getHandles().popFramesIncluding(frame);
            }
        };
        value.write(createHandle(c.asValue(executable)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_get_callback_info", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Retrieves details about the call within a callback (e.g., the arguments from a given callback info).",
                    "",
                    " @param callback_info from the callback.",
                    " @param argc number of arguments to the callback.",
                    " @param argv poly_value array of arguments for the callback.",
                    " @param the data pointer for the callback.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_get_callback_info(PolyglotIsolateThread thread, PolyglotCallbackInfo callback_info, SizeTPointer argc, PolyglotValuePointer argv, WordPointer data) {
        resetErrorState();
        PolyglotCallbackInfoInternal callbackInfo = fetchHandle(callback_info);
        UnsignedWord numberOfArguments = WordFactory.unsigned(callbackInfo.arguments.length);
        UnsignedWord bufferSize = argc.read();
        UnsignedWord size = bufferSize.belowThan(numberOfArguments) ? bufferSize : numberOfArguments;
        argc.write(size);
        for (UnsignedWord i = WordFactory.zero(); i.belowThan(size); i = i.add(1)) {
            int index = (int) i.rawValue();
            ObjectHandle argument = callbackInfo.arguments[index];
            argv.write(index, argument);
        }
        data.write(callbackInfo.data);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_throw_exception", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Raises an exception in a C callback.",
                    "",
                    "Invocation of this method does not interrupt control-flow so it is necessary to return from a function after ",
                    "the exception has been raised. If this method is called multiple times only the last exception will be thrown in",
                    "in the guest language.",
                    "",
                    " @param utf8_message 0 terminated error message.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_throw_exception(PolyglotIsolateThread thread, @CConst CCharPointer utf8_message) {
        resetErrorState();
        exceptionsTL.set(new CallbackException(CTypeConversion.utf8ToJavaString(utf8_message)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_delete_reference", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Deletes a poly_reference. After this point, the reference must not be used anymore.",
                    "",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_delete_reference(PolyglotIsolateThread thread, PolyglotNativeAPITypes.PolyglotReference reference) {
        resetErrorState();
        objectHandles.destroy(reference);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_reference", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a poly_reference from a poly_handle. After this point, the reference is alive until poly_delete_reference is called. ",
                    "",
                    "Handles are: poly_engine, poly_engine_builder, poly_context, poly_context_builder, poly_language, poly_value, ",
                    "and poly_callback_info.",
                    "",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_create_reference(PolyglotIsolateThread thread, PolyglotNativeAPITypes.PolyglotHandle handle, PolyglotNativeAPITypes.PolyglotReferencePointer reference) {

        resetErrorState();
        ObjectHandle ref = objectHandles.create(getHandles().getObject(handle));
        reference.write((PolyglotNativeAPITypes.PolyglotReference) ref);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_open_handle_scope", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Opens a handle scope. Until the scope is closed, all objects will belong to the newly created scope.",
                    "",
                    "Handles are: poly_engine, poly_engine_builder, poly_context, poly_context_builder, poly_language, poly_value, ",
                    "and poly_callback_info.",
                    "",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_open_handle_scope(PolyglotIsolateThread thread) {
        resetErrorState();
        getHandles().pushFrame(DEFAULT_FRAME_CAPACITY);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_close_handle_scope", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Closes a handle scope. After this point, the handles from the current scope must not be used anymore.",
                    "",
                    "Handles are: poly_engine, poly_engine_builder, poly_context, poly_context_builder, poly_language, poly_value, ",
                    "and poly_callback_info.",
                    "",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_close_handle_scope(PolyglotIsolateThread thread) {
        resetErrorState();
        getHandles().popFrame();
        return poly_ok;
    }

    @CEntryPoint(name = "poly_get_last_exception", exceptionHandler = GenericFailureExceptionHandler.class, documentation = {
                    "Returns the last exception that occurred on this thread, or does nothing if an exception did not happen.",
                    "",
                    "This method must be called right after an exception occurs (after a method returns poly_pending_exception), ",
                    "and can be called only once.",
                    "",
                    " @param result On success, a handle to the last exception on this thread is put here.",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_get_last_exception(PolyglotIsolateThread thread, PolyglotExceptionHandlePointer result) {
        ThreadLocalState state = threadLocals.get();
        if (state == null || state.polyglotException == null) {
            result.write(ThreadLocalHandles.nullHandle());
        } else {
            result.write(createHandle(state.polyglotException));
            state.polyglotException = null;
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_syntax_error", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if an exception is caused by a parser or syntax error.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param result The result of the check.",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_exception_is_syntax_error(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isSyntaxError()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_cancelled", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if execution has been cancelled.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param result The result of the check.",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_exception_is_cancelled(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isCancelled()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_internal_error", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if this exception was caused by an internal implementation error.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param result The result of the check.",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_exception_is_internal_error(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isInternalError()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_has_object", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if this exception has a guest language exception object attached to it.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param result The result of the check.",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_exception_has_object(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.getGuestObject() != null));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_object", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the handle to the guest exception object. This object can then be used in other poly methods.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param result The handle to the guest object if it exists.",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_exception_get_object(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, PolyglotValuePointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);
        Value guestObject = e.getGuestObject();
        if (guestObject == null) {
            reportError("Attempted to get the guest object of an exception that did not have one.", poly_generic_failure);
        } else {
            result.write(createHandle(guestObject));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_stack_trace", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the full stack trace as a UTF-8 encoded string.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param buffer Where to write the UTF-8 string representing the stack trace. Can be NULL.",
                    " @param buffer_size Size of the user-supplied buffer.",
                    " @param result If buffer is NULL, this will contain the buffer size required to put the trace string in, otherwise, it will contain the number of bytes written",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 19.0",
    })
    public static PolyglotStatus poly_exception_get_stack_trace(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        e.getPolyglotStackTrace().forEach(trace -> pw.println(trace));

        writeString(sw.toString(), buffer, buffer_size, result, UTF8_CHARSET);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_guest_stack_trace", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the guest stack trace as a UTF-8 encoded string.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param buffer Where to write the UTF-8 string representing the stack trace. Can be NULL.",
                    " @param buffer_size Size of the user-supplied buffer.",
                    " @param result If buffer is NULL, this will contain the buffer size required to put the trace string in, otherwise, it will contain the number of bytes written",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 22.3",
    })
    public static PolyglotStatus poly_exception_get_guest_stack_trace(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CCharPointer buffer, UnsignedWord buffer_size,
                    SizeTPointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        Iterable<PolyglotException.StackFrame> traceElements = e.getPolyglotStackTrace();
        for (PolyglotException.StackFrame trace : traceElements) {
            if (trace.isGuestFrame()) {
                pw.println(trace);
            }
        }
        writeString(sw.toString(), buffer, buffer_size, result, UTF8_CHARSET);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_message", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the error message as a UTF-8 encoded string.",
                    "",
                    " @param exception Handle to the exception object.",
                    " @param buffer Where to write the UTF-8 string representing the error message. Can be NULL.",
                    " @param buffer_size Size of the user-supplied buffer.",
                    " @param result If buffer is NULL, this will contain the buffer size required to put the error message string in, otherwise, it will contain the number of bytes written",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 22.3",
    })
    public static PolyglotStatus poly_exception_get_message(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CCharPointer buffer, UnsignedWord buffer_size,
                    SizeTPointer result) {
        resetErrorState();
        PolyglotException e = fetchHandle(exception);

        writeString(e.getMessage(), buffer, buffer_size, result, UTF8_CHARSET);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_register_recurring_callback", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Registers (or unregisters) a recurring callback in the current thread to be",
                    "called approximately at the specified interval. The callback's result value is",
                    "ignored. Any previously registered callback is replaced. Passing NULL for the",
                    "the callback function removes a previously registered callback (in which case",
                    "the interval and data parameters are ignored).",
                    "",
                    " @param intervalNanos interval between invocations in nanoseconds.",
                    " @param callback the function that is invoked.",
                    " @param data a custom pointer to be passed to each invocation of the callback.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 22.2",
    })
    @Uninterruptible(reason = "Prevent safepoint checks before pausing recurring callback.")
    public static PolyglotStatus poly_register_recurring_callback(PolyglotIsolateThread thread, long intervalNanos, PolyglotCallback callback, VoidPointer data) {
        ThreadingSupportImpl.pauseRecurringCallback("Prevent recurring callback execution before returning.");
        try {
            return doRegisterRecurringCallback(intervalNanos, callback, data);
        } finally {
            ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
        }
    }

    @Uninterruptible(reason = "Not really, but our caller is.", calleeMustBe = false)
    private static PolyglotStatus doRegisterRecurringCallback(long intervalNanos, PolyglotCallback callback, VoidPointer data) {
        return doRegisterRecurringCallback0(intervalNanos, callback, data);
    }

    private static PolyglotStatus doRegisterRecurringCallback0(long intervalNanos, PolyglotCallback callback, VoidPointer data) {
        resetErrorState();
        if (!ThreadingSupportImpl.isRecurringCallbackSupported()) {
            return poly_generic_failure;
        }
        if (callback.isNull()) {
            Threading.registerRecurringCallback(-1, null, null);
            return poly_ok;
        }
        PolyglotCallbackInfoInternal info = new PolyglotCallbackInfoInternal(new ObjectHandle[0], data);
        Threading.registerRecurringCallback(intervalNanos, TimeUnit.NANOSECONDS, access -> {
            int frame = getHandles().pushFrame(DEFAULT_FRAME_CAPACITY);
            try {
                PolyglotCallbackInfo infoHandle = (PolyglotCallbackInfo) createHandle(info);
                callback.invoke((PolyglotIsolateThread) CurrentIsolate.getCurrentThread(), infoHandle);
                CallbackException ce = exceptionsTL.get();
                if (ce != null) {
                    exceptionsTL.remove();
                    access.throwException(ce);
                }
            } finally {
                getHandles().popFramesIncluding(frame);
            }
        });
        return poly_ok;
    }

    private static class PolyglotCallbackInfoInternal {
        ObjectHandle[] arguments;
        VoidPointer data;

        PolyglotCallbackInfoInternal(ObjectHandle[] arguments, VoidPointer data) {
            this.arguments = arguments;
            this.data = data;
        }
    }

    @CEntryPoint(name = "poly_perf_data_get_address_of_int64_t", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the address of the int64_t value for a performance data entry of type long. Performance data support must be enabled.",
                    "",
                    " @param utf8_key UTF8-encoded, 0 terminated key that identifies the performance data entry.",
                    " @param result a pointer to which the address of the int64_t value will be written.",
                    " @return poly_ok if everything went ok, otherwise an error occurred.",
                    " @since 22.3",
    })
    public static PolyglotStatus poly_perf_data_get_address_of_int64_t(PolyglotIsolateThread thread, CCharPointer utf8Key, CInt64PointerPointer result) {
        resetErrorState();
        String key = CTypeConversion.utf8ToJavaString(utf8Key);
        if (!ImageSingletons.lookup(PerfDataSupport.class).hasLong(key)) {
            throw reportError("Key " + key + " is not a valid performance data entry key.", poly_generic_failure);
        }
        CInt64Pointer ptr = (CInt64Pointer) ImageSingletons.lookup(PerfDataSupport.class).getLong(key);
        result.write(ptr);
        return poly_ok;
    }

    private static void writeString(String valueString, CCharPointer buffer, UnsignedWord length, SizeTPointer result, Charset charset) {
        if (buffer.isNull()) {
            int stringLength = valueString.getBytes(charset).length;
            result.write(WordFactory.unsigned(stringLength));
        } else {
            result.write(CTypeConversion.toCString(valueString, charset, buffer, length));
        }
    }

    private static List<Language> sortedLangs(Engine engine) {

        return engine.getLanguages().entrySet().stream()
                        .sorted(Comparator.comparing(Entry::getKey))
                        .map(Entry::getValue)
                        .collect(Collectors.toList());
    }

    private static void resetErrorState() {
        ThreadLocalState state = ensureLocalsInitialized();
        resetErrorStateAtomically(state);
    }

    @Uninterruptible(reason = "An exception thrown by recurring callback can cause inconsistency, leaks or stale pointers.")
    private static void resetErrorStateAtomically(ThreadLocalState state) {
        state.lastErrorCode = poly_ok;
        state.lastException = null;
        freeUnmanagedErrorState(state);
    }

    @Uninterruptible(reason = "An exception thrown by recurring callback can cause inconsistency, leaks or stale pointers.")
    private static void freeUnmanagedErrorState(ThreadLocalState state) {
        if (state.lastExceptionUnmanagedInfo.isNonNull()) {
            assert state.lastExceptionUnmanagedInfo.getErrorMessage().isNonNull();

            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(state.lastExceptionUnmanagedInfo.getErrorMessage());
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(state.lastExceptionUnmanagedInfo);
            state.lastExceptionUnmanagedInfo = WordFactory.nullPointer();
        }
    }

    private static RuntimeException reportError(String message, PolyglotStatus errorCode) {
        throw new PolyglotNativeAPIError(errorCode, message);
    }

    private static final class ExceptionHandler implements CEntryPoint.ExceptionHandler {
        @Uninterruptible(reason = "exception handler")
        static PolyglotStatus handle(Throwable t) {
            ThreadLocalState state = threadLocals.get();
            if (state == null) { // caught exception from recurring callback early during init?
                return poly_generic_failure;
            }
            PolyglotStatus errorCode = t instanceof PolyglotNativeAPIError ? ((PolyglotNativeAPIError) t).getCode() : poly_generic_failure;
            if (t instanceof PolyglotException) {
                // We should never have both a PolyglotException and an error at the same time
                state.polyglotException = (PolyglotException) t;
                errorCode = poly_pending_exception;
            }
            state.lastException = t;
            state.lastErrorCode = errorCode;
            return errorCode;
        }
    }

    private static final class GenericFailureExceptionHandler implements CEntryPoint.ExceptionHandler {
        @Uninterruptible(reason = "exception handler")
        static PolyglotStatus handle(Throwable t) {
            return poly_generic_failure;
        }
    }

    private static PolyglotNativeAPITypes.PolyglotHandle createHandle(Object result) {
        return getHandles().create(result);
    }

    private static <T> T fetchHandle(PolyglotNativeAPITypes.PolyglotHandle object) {
        if (object.equal(ThreadLocalHandles.nullHandle())) {
            return null;
        }

        if (ThreadLocalHandles.isInRange(object)) {
            return getHandles().getObject(object);
        }

        if (objectHandles.isInRange(object)) {
            return objectHandles.get(object);
        }

        throw new RuntimeException("Invalid poly_reference or poly_handle.");
    }

    public static class CallbackException extends RuntimeException {
        static final long serialVersionUID = 123123098097526L;

        CallbackException(String message) {
            super(message);
        }
    }
}
