package org.eclipse.n4js.json.validation.extension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.DiagnosticChain;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.n4js.json.JSON.JSONDocument;
import org.eclipse.n4js.json.JSON.JSONObject;
import org.eclipse.n4js.json.JSON.JSONPackage;
import org.eclipse.n4js.json.JSON.JSONValue;
import org.eclipse.n4js.json.JSON.NameValuePair;
import org.eclipse.n4js.json.validation.JSONIssueCodes;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.EValidatorRegistrar;
import org.eclipse.xtext.xbase.lib.Pair;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

/**
 * An abstract implementation of {@link IJSONValidatorExtension} that enables the functionality of an
 * {@link AbstractDeclarativeValidator} (check methods, addIssue methods, etc.).
 * 
 * Subclasses of this class can be registered as {@code org.eclipse.n4js.json.validation} extension.
 * 
 * Subclasses may further customize their scope by overriding {@link #isResponsible(Map, EObject)} so that they only
 * apply to certain resources.
 */
public abstract class AbstractJSONValidatorExtension extends AbstractDeclarativeValidator
		implements IJSONValidatorExtension {
	
	private static final Logger LOGGER = Logger.getLogger(AbstractJSONValidatorExtension.class);

	private static final String JSON_DOCUMENT_VALUES = "JSON_DOCUMENT_VALUES";
	private static final String JSON_DOCUMENT = "JSON_DOCUMENT";

	@Override
	public final void validateJSON(JSONDocument document, DiagnosticChain diagnosticChain) {
		// use a new empty context for validator extension
		Map<Object, Object> context = new HashMap<>();

		// early exit, if this validator is not responsible for this particular document
		if (!this.isResponsible(context, document)) {
			return;
		}

		// allow access to validated JSONDocument instance via the validation context
		context.put(JSON_DOCUMENT, document);

		// validate document itself
		this.validate(JSONPackage.Literals.JSON_DOCUMENT, document, diagnosticChain, context);
		
		// validate all contents
		document.eAllContents().forEachRemaining(child -> {
			this.validate(child.eClass(), child, diagnosticChain, context);
		});
	}

	/**
	 * Collects all {@link CheckProperty} methods of this class and invokes them on the corresponding properties in the
	 * given JSON document
	 */
	@Check
	public void checkUsingCheckPropertyMethods(JSONDocument document) {
		final List<Method> allMethods = Arrays.asList(this.getClass().getDeclaredMethods());
		final List<Pair<CheckProperty, Method>> checkKeyMethods = allMethods.stream()
				// filter for methods with CheckProperty annotation
				.filter(m -> m.getAnnotationsByType(CheckProperty.class).length != 0)
				// filter for methods with a single parameter type
				.filter(m -> isValidCheckKeyMethod(m))
				// map to annotation instance + method pair
				.map(m -> Pair.of(m.getAnnotationsByType(CheckProperty.class)[0], m))
				.collect(Collectors.toList());

		final Multimap<String, JSONValue> documentValues = collectDocumentValues(document);

		for (Pair<CheckProperty, Method> methodPair : checkKeyMethods) {
			final CheckProperty annotation = methodPair.getKey();
			final Method method = methodPair.getValue();

			final String keyPath = annotation.propertyPath();
			final Collection<JSONValue> values = documentValues.get(keyPath);
			
			// check each value that has been specified for keyPath
			for (JSONValue value : values) {
				if (value != null) {
					try {
						// invoke method without any or with value as single argument
						if (method.getParameterTypes().length == 0) {
							method.invoke(this);
						} else {
							method.invoke(this, value);
						}
					} catch (IllegalAccessException | IllegalArgumentException e) {
						throw new IllegalStateException("Failed to invoke @CheckProperty method " + method + ": " + e);
					} catch (InvocationTargetException e) {
						LOGGER.error("Failed to invoke @CheckProperty method " + method + ": " + e.getTargetException());
						e.getTargetException().printStackTrace();
					}
				}
			}
		}
	}

	@Override
	public void register(EValidatorRegistrar registrar) {
		// do not register with EMF registry (uses designated extension points instead)
	}

	/**
	 * Returns a multimap that allows keyPath-access to all values that occur in the currently validated
	 * {@link JSONDocument}.
	 * 
	 * A key-path is a dot separated specifier of nested property access (e.g. {@code n4js.sources}).
	 */
	protected Multimap<String, JSONValue> getDocumentValues() {
		// collect all document values for key-path-based access in check methods and
		// store resulting multimap in the validation context
		return contextMemoize(JSON_DOCUMENT_VALUES, () -> {
			return collectDocumentValues((JSONDocument) this.getContext().get(JSON_DOCUMENT));
		});
	}

	/**
	 * Memoizes the value as given by {@code supplier} in the current validation context and returns its value.
	 * 
	 * Only invokes {@code supplier} once and re-uses its result in later invocations of this method with the same
	 * {@code key}.
	 */
	@SuppressWarnings("unchecked")
	protected <T> T contextMemoize(String key, Supplier<T> supplier) {
		// first check context for cached multimap of the document values
		if (this.getContext().containsKey(key)) {
			return (T) this.getContext().get(key);
		}

		final T value = supplier.get();
		this.getContext().put(key, value);
		return value;
	}

	/**
	 * Returns the collection of {@link JSONValue} that have been associated with the given key-path (includes
	 * duplicates).
	 */
	protected Collection<JSONValue> getDocumentValue(String keyPath) {
		return getDocumentValues().get(keyPath);
	}
	
	/**
	 * Returns one of the {@link JSONValue}s that have been associated with the given key-path (ignores duplicates).
	 * 
	 * The {@link JSONValue} at the given key-path must be of type {@code expectedClass}. If this is not the
	 * case, this method returns {@code null}.
	 * 
	 * Returns {@code null} if no value has been associated with the given {@code keyPath}.
	 */
	protected <T extends JSONValue> T getSingleDocumentValue(String keyPath, Class<T> expectedClass) {
		Collection<JSONValue> values = getDocumentValues().get(keyPath);
		final JSONValue value = !values.isEmpty() ? values.iterator().next() : null;
		if (!expectedClass.isInstance(value)) {
			return null;
		}
		return expectedClass.cast(value);
	}
	
	/**
	 * Returns one of the {@link JSONValue}s that have been associated with the given key-path (ignores duplicates).
	 * 
	 * Returns {@code null} if no value has been associated with the given {@code keyPath}.
	 */
	protected JSONValue getSingleDocumentValue(String keyPath) {
		return this.getSingleDocumentValue(keyPath, JSONValue.class);
	}

	/** Returns a user-facing description of the given {@link JSONValue} */
	protected static String getJSONValueDescription(JSONValue value) {
		return getJSONValueDescription(value.eClass());
	}

	/** Returns a user-facing description of the given {@link JSONValue} subclass. */
	protected static String getJSONValueDescription(EClass valueClass) {
		if (valueClass == JSONPackage.eINSTANCE.getJSONStringLiteral()) {
			return "string";
		} else if (valueClass == JSONPackage.eINSTANCE.getJSONNumericLiteral()) {
			return "number";
		} else if (valueClass == JSONPackage.eINSTANCE.getJSONBooleanLiteral()) {
			return "boolean";
		} else if (valueClass == JSONPackage.eINSTANCE.getJSONObject()) {
			return "object";
		} else if (valueClass == JSONPackage.eINSTANCE.getJSONArray()) {
			return "array";
		} else {
			return "<unknown>";
		}
	}

	@Override
	protected List<EPackage> getEPackages() {
		// by default, JSON validator extensions only apply to elements of the JSON model
		return Arrays.asList(JSONPackage.eINSTANCE);
	}

	@Override
	protected boolean isResponsible(Map<Object, Object> context, EObject eObject) {
		// by default, JSON validator extensions are responsible for all elements of the JSON model
		return eObject.eClass().getEPackage() == JSONPackage.eINSTANCE;
	}

	/**
	 * Checks that in the given JSON {@code documentValues}, a value has been set for the given property path.
	 *
	 * Adds an {@code IssueCodes#JSON_MISSING_PROPERTY} issue to {@code issueTarget} otherwise.
	 */
	protected void checkIsPresent(EObject issueTarget, Multimap<String, JSONValue> documentValues,
			String propertyPath) {
		if (!documentValues.containsKey(propertyPath)) {
			addIssue(JSONIssueCodes.getMessageForJSON_MISSING_PROPERTY(propertyPath), issueTarget,
					JSONIssueCodes.JSON_MISSING_PROPERTY);
		}
	}

	/**
	 * Similar to super method but with an adjusted issue severity computation using {@link #getJSONSeverity(String)}.
	 */
	@Override
	protected void addIssue(String message, EObject source, EStructuralFeature feature, int index, String issueCode,
			String... issueData) {
		// use custom getJSONSeverity instead of getIssueSeverities()
		Severity severity = getJSONSeverity(issueCode);
		if (severity != null) {
			switch (severity) {
			case WARNING:
				getMessageAcceptor().acceptWarning(message, source, feature, index, issueCode, issueData);
				break;
			case INFO:
				getMessageAcceptor().acceptInfo(message, source, feature, index, issueCode, issueData);
				break;
			case ERROR:
				getMessageAcceptor().acceptError(message, source, feature, index, issueCode, issueData);
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Computes the severity of the given issue code.
	 * 
	 * First considers severities as defined by {@link JSONIssueCodes}. If this is not successful, this method performs
	 * the common severity computation via {@link #getIssueSeverities(Map, EObject)}.
	 * 
	 * In extensions that extend this class, this will lead to a behavior in which first the JSON bundle severities are
	 * considered and then the issue severities of the bundle that provides an extension.
	 */
	private Severity getJSONSeverity(String issueCode) {
		final Severity jsonSeverity = JSONIssueCodes.getDefaultSeverity(issueCode);
		if (null != jsonSeverity) {
			return jsonSeverity;
		}
		return getIssueSeverities(getContext(), getCurrentObject()).getSeverity(issueCode);
	}

	/**
	 * Checks that the given JSON {@code value} is an instance of the given {@code valueClass}.
	 *
	 * Adds an {@link JSONIssueCodes#JSON_EXPECTED_DIFFERENT_VALUE_TYPE} to {@code value} if not.
	 */
	protected boolean checkIsType(JSONValue value, EClass valueClass) {
		return checkIsType(value, valueClass, "");
	}

	/**
	 * Checks that the given JSON {@code value} is an instance of the given {@code valueClass}.
	 *
	 * Adds an {@link JSONIssueCodes#JSON_EXPECTED_DIFFERENT_VALUE_TYPE} to {@code value} if not.
	 * 
	 * @param value
	 *            The value whose type to check.
	 * @param valueClass
	 *            The expected class
	 * @param locationClause
	 *            Additional clause to append to the error message to improve the validation message quality (e.g.
	 *            Expected string instead of object <<for property 'Z'>>.)
	 */
	protected boolean checkIsType(JSONValue value, EClass valueClass, String locationClause) {
		// add no issue in case of a broken AST
		if (null == value) {
			return false;
		}
		if (!valueClass.isInstance(value)) {
			addIssue(JSONIssueCodes.getMessageForJSON_EXPECTED_DIFFERENT_VALUE_TYPE(getJSONValueDescription(valueClass),
					getJSONValueDescription(value), locationClause), value,
					JSONIssueCodes.JSON_EXPECTED_DIFFERENT_VALUE_TYPE);
			return false;
		}
		return true;
	}

	/** 
	 * Returns {@code true} iff {@code method} is a valid {link CheckProperty} method. 
	 * 
	 * A valid {@link CheckProperty} is a valid without any or with only one parameter of type {@link JSONValue}. 
	 * */
	private boolean isValidCheckKeyMethod(Method method) {
		return method.getParameterTypes().length == 0 ||
				(method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == JSONValue.class);
	}

	/**
	 * Collects all key-path-value associations that can be extracted from the given JSON {@code document}.
	 */
	protected Multimap<String, JSONValue> collectDocumentValues(JSONDocument document) {
		if (document == null || document.getContent() == null) {
			return ImmutableMultimap.<String, JSONValue> of(); // empty map
		}

		final JSONValue documentContent = document.getContent();
		if (!(documentContent instanceof JSONObject)) {
			return ImmutableMultimap.<String, JSONValue> of(); // empty map
		}

		return collectDocumentValues((JSONObject) documentContent,
				LinkedHashMultimap.<String, JSONValue> create(), "", -1);
	}

	/**
	 * Collect all name-value associations that can be extracted from the given {@link JSONObject}.
	 * 
	 * Does not recursively traverse any nested objects.
	 */
	protected Multimap<String, JSONValue> collectObjectValues(JSONObject object) {
		if (object == null) {
			return ImmutableMultimap.<String, JSONValue> of(); // empty map
		}

		return collectDocumentValues(object,
				LinkedHashMultimap.<String, JSONValue> create(), "", 1);
	}

	/**
	 * Collects all key-path-value pairs that can be extracted from the given {@code object} and stores them in
	 * {@code documentValues}.
	 *
	 * Key-paths are represented in terms of concatenated strings, separated by a {@code .} character (e.g.
	 * "nested.name.value" => JSONValue).
	 *
	 * @param object
	 *            The {@link JSONObject} to collect the values form.
	 * @param documentValues
	 *            The map to store the values to.
	 * @param prefix
	 *            The prefix to use for key-paths.
	 * @param depth
	 *            The depth up to which the given {@link JSONObject} should be traversed. If {@code -1} no depth limit
	 *            is assumed.
	 */
	private Multimap<String, JSONValue> collectDocumentValues(JSONObject object,
			Multimap<String, JSONValue> documentValues, String prefix, int depth) {
		// If maximum depth has been reached, do not traverse object further.
		// For negative depths this will always evaluate to false -> no depth limit
		if (depth == 0) {
			return documentValues;
		}

		for (NameValuePair pair : object.getNameValuePairs()) {
			final String pairName = pair.getName();
			final String name = prefix.isEmpty() ? pairName : prefix + "." + pairName;
			final JSONValue value = pair.getValue();
			documentValues.put(name, value);

			if (value instanceof JSONObject) {
				// recursively collect all values from pair value
				collectDocumentValues((JSONObject) value, documentValues, name, depth - 1);
			}
		}
		return documentValues;
	}
}
