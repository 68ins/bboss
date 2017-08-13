package org.frameworkset.web.request.async;

import java.util.List;
import java.util.concurrent.Callable;

import org.frameworkset.web.servlet.mvc.NativeWebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assists with the invocation of {@link CallableProcessingInterceptor}'s.
 *
 * @author Rossen Stoyanchev
 * @author Rob Winch
 * @since 3.2
 */
class CallableInterceptorChain {

	protected static final Logger logger = LoggerFactory.getLogger(CallableInterceptorChain.class);
	private final List<CallableProcessingInterceptor> interceptors;

	private int preProcessIndex = -1;


	public CallableInterceptorChain(List<CallableProcessingInterceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public void applyBeforeConcurrentHandling(NativeWebRequest request, Callable<?> task) throws Exception {
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			interceptor.beforeConcurrentHandling(request, task);
		}
	}

	public void applyPreProcess(NativeWebRequest request, Callable<?> task) throws Exception {
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			interceptor.preProcess(request, task);
			this.preProcessIndex++;
		}
	}

	public Object applyPostProcess(NativeWebRequest request, Callable<?> task, Object concurrentResult) {
		Throwable exceptionResult = null;
		for (int i = this.preProcessIndex; i >= 0; i--) {
			try {
				this.interceptors.get(i).postProcess(request, task, concurrentResult);
			}
			catch (Throwable t) {
				// Save the first exception but invoke all interceptors
				if (exceptionResult != null) {
					logger.error("postProcess error", t);
				}
				else {
					exceptionResult = t;
				}
			}
		}
		return (exceptionResult != null) ? exceptionResult : concurrentResult;
	}

	public Object triggerAfterTimeout(NativeWebRequest request, Callable<?> task) {
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			try {
				Object result = interceptor.handleTimeout(request, task);
				if (result == CallableProcessingInterceptor.RESPONSE_HANDLED) {
					break;
				}
				else if (result != CallableProcessingInterceptor.RESULT_NONE) {
					return result;
				}
			}
			catch (Throwable t) {
				return t;
			}
		}
		return CallableProcessingInterceptor.RESULT_NONE;
	}

	public void triggerAfterCompletion(NativeWebRequest request, Callable<?> task) {
		for (int i = this.interceptors.size()-1; i >= 0; i--) {
			try {
				this.interceptors.get(i).afterCompletion(request, task);
			}
			catch (Throwable t) {
				logger.error("afterCompletion error", t);
			}
		}
	}
}
