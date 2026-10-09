// Things the library expects from a browser that the mod's embedded JavaScript engine lacks.
if (typeof globalThis === 'undefined') this.globalThis = this;
var __sentilsId = 0;
var crypto = {
	randomUUID: function () {
		__sentilsId++;
		return '00000000-0000-4000-8000-' + ('000000000000' + __sentilsId.toString(16)).slice(-12);
	},
};
if (typeof structuredClone === 'undefined') {
	var structuredClone = function (value) {
		return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
	};
}
