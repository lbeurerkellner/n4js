// Generated by N4JS transpiler; for copyright see original N4JS source file.

(function(System) {
	'use strict';
	System.register([
		'org.eclipse.n4js.spec.tests/src-gen/Ch08_02_01_SuperKeyword/superGetterSetterAccess/remote/C1'
	], function($n4Export) {
		var C1, C2;
		C2 = function C2() {
			C1.prototype.constructor.call(this);
		};
		$n4Export('C2', C2);
		return {
			setters: [
				function($exports) {
					// org.eclipse.n4js.spec.tests/src-gen/Ch08_02_01_SuperKeyword/superGetterSetterAccess/remote/C1
					C1 = $exports.C1;
				}
			],
			execute: function() {
				$makeClass(C2, C1, [], {}, {}, function(instanceProto, staticProto) {
					var metaClass = new N4Class({
						name: 'C2',
						origin: 'org.eclipse.n4js.spec.tests',
						fqn: 'Ch08_02_01_SuperKeyword.superGetterSetterAccess.remote.C2.C2',
						n4superType: C1.n4type,
						allImplementedInterfaces: [
							'Ch08_02_01_SuperKeyword.superGetterSetterAccess.remote.I3.I3',
							'Ch08_02_01_SuperKeyword.superGetterSetterAccess.remote.I2.I2',
							'Ch08_02_01_SuperKeyword.superGetterSetterAccess.remote.I1.I1'
						],
						ownedMembers: [],
						consumedMembers: [],
						annotations: []
					});
					return metaClass;
				});
				Object.defineProperty(C2, '$di', {
					value: {
						superType: C1,
						fieldsInjectedTypes: []
					}
				});
			}
		};
	});
})(typeof module !== 'undefined' && module.exports ? require('n4js-node').System(require, module) : System);
//# sourceMappingURL=C2.map
