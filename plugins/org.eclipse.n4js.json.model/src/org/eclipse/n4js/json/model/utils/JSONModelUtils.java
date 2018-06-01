/**
 * Copyright (c) 2018 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.json.model.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.n4js.json.JSON.JSONArray;
import org.eclipse.n4js.json.JSON.JSONFactory;
import org.eclipse.n4js.json.JSON.JSONObject;
import org.eclipse.n4js.json.JSON.JSONPackage;
import org.eclipse.n4js.json.JSON.JSONStringLiteral;
import org.eclipse.n4js.json.JSON.JSONValue;
import org.eclipse.n4js.json.JSON.NameValuePair;

/**
 * Utility methods for more convenient access to elements of the {@link JSONPackage} model.
 */
public class JSONModelUtils {

	/**
	 * Returns the {@link JSONValue} that can be found under the given property path starting from the given
	 * {@code object}.
	 *
	 * Returns an absent {@link Optional} in case the path cannot be resolved (e.g. non-existing properties or values of
	 * non-object type).
	 *
	 * @throws JSONPropertyPathException
	 *             if the given path cannot be resolve on {@code object}.
	 */
	public static Optional<JSONValue> getPath(JSONObject object, List<String> path) {
		if (path.isEmpty()) {
			return Optional.empty();
		}
		final String currentProperty = path.get(0);
		final JSONValue propertyValue = getProperty(object, currentProperty).orElse(null);

		// check that the current property can be resolved
		if (propertyValue == null) {
			return Optional.empty();
		}

		// in case of the last segment
		if (path.size() == 1) {
			// simply return the value
			return Optional.ofNullable(propertyValue);
		}

		// otherwise, check that the property resolves to an JSONObject
		if (!(propertyValue instanceof JSONObject)) {
			return Optional.empty();
		}

		final JSONObject targetObject = (JSONObject) propertyValue;
		// recursively get sub-path of path on targetObject
		return getPath(targetObject, path.subList(1, path.size()));
	}

	/**
	 * Sets the string {@code value} for the given property (dot-delimited) {@code path} starting from {@code objec†}.
	 */
	public static JSONStringLiteral setPath(JSONObject object, String path, String value) {
		return setPath(object, Arrays.asList(path.split("\\.")), createStringLiteral(value));
	}

	/**
	 * Sets the {@code value} for the given property (dot-delimited) {@code path} starting from {@code objec†}.
	 */
	public static <V extends JSONValue> V setPath(JSONObject object, String path, V value) {
		return setPath(object, Arrays.asList(path.split("\\.")), value);
	}

	/**
	 * Sets the {@code value} for the given property {@code path} starting from {@code objec†}.
	 */
	public static <V extends JSONValue> V setPath(JSONObject object, List<String> path, V value) {
		try {
			return setPath(object, path, path, value);
		} catch (JSONPropertyPathException e) {
			throw new JSONPropertyPathException("Failed to resolve JSON property path " + path, e);
		}
	}

	private static <V extends JSONValue> V setPath(JSONObject object, List<String> currentPath,
			List<String> fullPath, V value) {
		if (currentPath.size() == 0) {
			return null;
		}

		final String currentProperty = currentPath.get(0);
		final int pathLength = currentPath.size();

		// if we are at the end of the path
		if (pathLength == 1) {
			// set the value on 'object'
			setProperty(object, currentProperty, value);
			return value;
		}

		// obtain NameValuePair that matches the first segment in propertyPath
		final Optional<NameValuePair> pair = object.getNameValuePairs().stream()
				.filter(p -> p.getName().equals(currentProperty)).findAny();

		// if pair already exists
		if (pair.isPresent()) {
			final JSONValue pathValue = pair.get().getValue();

			// check whether the value is an object
			if (!(pathValue instanceof JSONObject)) {
				// if not, the property path is invalid
				throw new JSONPropertyPathException("Cannot resolve JSON property path further then " +
						fullPath.subList(0, fullPath.size() - pathLength).stream()
								.collect(Collectors.joining("."))
						+ ". " + pathValue + " is not a JSONObject.", null);
			}
			// setPath recursively on the (object) value of the existing pair
			return setPath((JSONObject) pathValue, currentPath.subList(1, pathLength), fullPath, value);
		} else {
			// add new object name-value-pair for current property
			final JSONObject nextObject = addProperty(object, currentProperty,
					JSONFactory.eINSTANCE.createJSONObject());
			return setPath(nextObject, currentPath.subList(1, pathLength), fullPath, value);
		}
	}

	/**
	 * Returns the value of the given {@code property} of {@code object}, or an absent optional if no value has been set
	 * for the given {@code property}.
	 */
	public static Optional<JSONValue> getProperty(JSONObject object, String property) {
		return object.getNameValuePairs().stream()
				.filter(pair -> pair.getName().equals(property))
				.findFirst()
				.map(pair -> pair.getValue());
	}

	/**
	 * Adds a new {@link NameValuePair} to the {@code object}, with given {@code name} and {@code value}.
	 *
	 * Does not check {@code object} for duplicate {@link NameValuePair} with the same name.
	 *
	 * @returns The newly set value.
	 */
	public static <V extends JSONValue> V addProperty(JSONObject object, String name, V value) {
		final NameValuePair nameValuePair = JSONFactory.eINSTANCE.createNameValuePair();
		nameValuePair.setName(name);
		nameValuePair.setValue(value);

		object.getNameValuePairs().add(nameValuePair);

		return value;
	}

	/**
	 * Sets property {@code name} to {@code value}.
	 *
	 * Looks for a name-value-pair in {@link JSONObject#getNameValuePairs()} with the given {@code name} and replaces
	 * its value.
	 *
	 * Adds a new name-value-pair if no such existing pair can be found.
	 *
	 * @returns The newly set value.
	 */
	public static <V extends JSONValue> V setProperty(JSONObject object, String name, V value) {
		// find existing pair
		final Optional<NameValuePair> existingPair = object.getNameValuePairs().stream()
				.filter(pair -> pair.getName().equals(name))
				.findAny();

		if (existingPair.isPresent()) {
			// change existing pair value
			existingPair.get().setValue(value);
		} else {
			// add new pair
			addProperty(object, name, value);
		}
		return value;
	}

	/**
	 *
	 * Sets property {@code name} to a JSON representation of string {@code value}.
	 *
	 * See {@link #addProperty(JSONObject, String, JSONValue)} and {@link #createStringLiteral(String)}
	 */
	public static JSONStringLiteral addProperty(JSONObject object, String name, String value) {
		return addProperty(object, name, createStringLiteral(value));
	}

	/**
	 * Sets property {@code name} to a JSON representation of string {@code value}.
	 *
	 * See {@link #setProperty(JSONObject, String, JSONValue)} and {@link #createStringLiteral(String)}
	 */
	public static JSONStringLiteral setProperty(JSONObject object, String name, String value) {
		return addProperty(object, name, createStringLiteral(value));
	}

	/**
	 * Creates a new {@link JSONStringLiteral} with the given string {@code value}.
	 */
	public static JSONStringLiteral createStringLiteral(String value) {
		final JSONStringLiteral literal = JSONFactory.eINSTANCE.createJSONStringLiteral();
		literal.setValue(value);
		return literal;
	}

	/**
	 * Creates a new {@link JSONArray} with the given {@code values} as elements.
	 */
	public static JSONArray createArray(Collection<JSONValue> values) {
		JSONArray result = JSONFactory.eINSTANCE.createJSONArray();
		result.getElements().addAll(values);
		return result;
	}

	/**
	 * Creates a new {@link JSONArray} with the given string {@code values} as elements.
	 *
	 * See {@link #createArray(Collection)} and {@link #createStringLiteral(String)}.
	 */
	public static JSONArray createStringArray(Collection<String> values) {
		JSONArray result = JSONFactory.eINSTANCE.createJSONArray();
		values.forEach(v -> result.getElements().add(createStringLiteral(v)));
		return result;
	}

	/**
	 * Designated exception that may be raised when using property path based methods with regard to {@link JSONObject}
	 * instances.
	 *
	 * @See {@link JSONModelUtils#setPath(JSONObject, List, JSONValue)}
	 */
	public static final class JSONPropertyPathException extends RuntimeException {
		/**
		 * Instantiates a new {@link JSONPropertyPathException} with the given message.
		 */
		public JSONPropertyPathException(String message) {
			super(message);
		}

		/**
		 * Instantiates a new {@link JSONPropertyPathException} with the given message wrapping around
		 * {@code exception}.
		 */
		public JSONPropertyPathException(String message, Exception exception) {
			super(message, exception);
		}
	}
}