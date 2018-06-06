(function(System) {
	System.registerDynamic([], true, function(require, exports, module) {
		/*
		 * Copyright (c) 2016 NumberFour AG.
		 * All rights reserved. This program and the accompanying materials
		 * are made available under the terms of the Eclipse Public License v1.0
		 * which accompanies this distribution, and is available at
		 * http://www.eclipse.org/legal/epl-v10.html
		 *
		 * Contributors:
		 *   NumberFour AG - Initial API and implementation
		 */
		
		export function foo() {
		    console.log("I'm invisible to N4JS");
		}
	});
})(typeof module !== 'undefined' && module.exports ? require('n4js-node').System(require, module) : System);
