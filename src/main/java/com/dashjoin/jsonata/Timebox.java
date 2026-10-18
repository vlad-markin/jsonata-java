/**
 * jsonata-java is the JSONata Java reference port
 * 
 * Copyright Dashjoin GmbH. https://dashjoin.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dashjoin.jsonata;

import com.dashjoin.jsonata.Jsonata.Frame;

/**
 * Configure max runtime / max recursion depth.
 * See Frame.setRuntimeBounds - usually not used directly
 */
public class Timebox {

    long timeout = 5000L;
    int maxDepth = 100;

    /** state of the evaluation currently running on this thread (identified by its base frame) */
    static final class State {
        Frame base;
        long time;
        int depth;
    }

    final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);

    /** depth of the last evaluation step on any thread (kept for diagnostics and tests) */
    int depth = 0;

    /**
     * Protect the process/browser from a runnaway expression
     * i.e. Infinite loop (tail recursion), or excessive stack growth
     *
     * Like the timebox of the jsonata-js test-suite: the time limit applies to each evaluation
     * (measured from its start, also when the frame is reused) and violations raise U1001.
     *
     * @param {Object} expr - expression to protect
     * @param {Number} timeout - max time in ms
     * @param {Number} maxDepth - max stack depth
     */
    public Timebox(Frame expr) {
        this(expr, 5000L, 100);
    }

    public Timebox(Frame expr, long timeout, int maxDepth) {
        this.timeout = timeout;
        this.maxDepth = maxDepth;

        // register callbacks
        expr.setEvaluateEntryCallback( (_exp, _input, _env)-> {
            if (_env.isParallelCall) return;
            State st = current(_env);
            depth = ++st.depth;
            checkRunnaway(st);
        });
        expr.setEvaluateExitCallback( (_exp, _input, _env, _res)-> {
            if (_env.isParallelCall) return;
            State st = current(_env);
            depth = --st.depth;
            checkRunnaway(st);
        });
    }

    State current(Frame env) {
        State st = state.get();
        Frame base = env != null ? env.base : null;
        if (st.base != base) {
            // a new evaluation started on this thread
            st.base = base;
            st.time = System.currentTimeMillis();
            st.depth = 0;
        }
        return st;
    }

    void checkRunnaway(State st) {
        if (st.depth > maxDepth) {
            // stack too deep
            throw new JException("U1001", -1,
                "Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive.");
        }
        if (System.currentTimeMillis() - st.time > timeout) {
            // expression has run for too long
            throw new JException("U1001", -1, "Expression evaluation timeout: Check for infinite loop");
        }
    };

}
