package ch.lambdaj.function.argument;

import static ch.lambdaj.function.argument.ArgumentsFactory.*;

import java.lang.reflect.*;
import java.util.concurrent.atomic.*;

import net.sf.cglib.proxy.*;

public class ProxyArgument implements MethodInterceptor {
	
	private Integer rootArgumentId;
	
	private Class<?> proxiedClass;
	
	private InvocationSequence invocationSequence;
	
	private int proxyId;
	
	ProxyArgument(Integer rootArgumentId, Class<?> proxiedClass, InvocationSequence invocationSequence) {
		this.rootArgumentId = rootArgumentId;
		this.proxiedClass = proxiedClass;
		this.invocationSequence = invocationSequence;
		proxyId = placeholderCounter.addAndGet(1);
	}

	public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
		String methodName = method.getName(); 
		if (methodName.equals("hashCode")) return hashCode();
		if (methodName.equals("equals")) return equals(args[0]);
		
		// Add this invocation to the current invocation sequence
		InvocationSequence currentInvocationSequence = new InvocationSequence(invocationSequence, new Invocation(proxiedClass, method, args));
		Class<?> returnClass = method.getReturnType();
		
		// Creates a new proxy propagating the invocation sequence
		return !Modifier.isFinal(returnClass.getModifiers()) ? 
				createArgument(rootArgumentId, returnClass, currentInvocationSequence) : 
				createPlaceholder(rootArgumentId, returnClass, currentInvocationSequence);
	}
	
	@Override
	public boolean equals(Object other) {
		return other instanceof ProxyArgument ? proxyId == ((ProxyArgument)other).proxyId : false;
	}
	
	@Override
	public int hashCode() {
		return proxyId;
	}
	
	private Object createPlaceholder(Integer rootArgumentId, Class<?> clazz, InvocationSequence invocationSequence) {
		// If the returned class is final it just returns a dummy object (of the right class) that acts as a 
		// place holder allowing to bind this proxy argument to the actual one. This means that you can't do a further invocation
		// on this sequence since there is no way to generate a proxy for this object 
		Object result = getPlaceholder(invocationSequence);
		if (result == null) {
			result = createArgumentPlaceholder(clazz);
			// Binds the result to this argument. It will be used as a place holder to retrieve the argument itself
			bindPlaceholder(invocationSequence, result, new Argument(rootArgumentId, invocationSequence));
		}
		return result;
	}

	private static AtomicInteger placeholderCounter = new AtomicInteger(Integer.MIN_VALUE);
	
	private Object createArgumentPlaceholder(Class<?> clazz) {
		Integer i = placeholderCounter.addAndGet(1);
		if (clazz.isPrimitive()) return getPrimitivePlaceHolder(clazz, i);
		if (clazz.isAssignableFrom(String.class)) return String.valueOf(i);
		if (clazz.isArray()) return new Object[0];

		try {
			return clazz.newInstance();
		} catch (Exception e) {
			return null;
		}
	}
	
	private Object getPrimitivePlaceHolder(Class<?> clazz, Integer i) {
		if (clazz == Integer.TYPE) return i;
		if (clazz == Character.TYPE) return Character.forDigit(i % Character.MAX_RADIX, Character.MAX_RADIX);
		if (clazz == Byte.TYPE) return Byte.valueOf(i.byteValue());
		if (clazz == Short.TYPE) return Short.valueOf(i.shortValue());
		if (clazz == Long.TYPE) return Long.valueOf(i.longValue());
		if (clazz == Float.TYPE) return Float.valueOf(i.floatValue());
		if (clazz == Double.TYPE) return Double.valueOf(i.doubleValue());
		if (clazz == Boolean.TYPE) return i % 2 == 0 ? Boolean.TRUE : Boolean.FALSE;
		return null;
	}
}