const { spawn, spawnSync } = require('child_process');
const { back, backRTE } = require('./log.js');

const isAsciiSoc = socFile => callSync('file', socFile).includes('ASCII');
const log = require('./log.js');

const model = () => process.env['ATFSTMODEL'] || process.env['ATKEITMODEL'];
const t31api = {
	pwd         : ['fspwd'],
	cd          : ['fscd'],
	stat        : ['fsstat', '--system'],
	who         : ['fswho'],
	proset      : ['fsproset'],
	proreset    : ['fsproreset'],
	clear       : ['fsclear'],
	logstart    : ['fssplogstart'],
	errlogstart : ['fserrlogstart'],
	log         : ['fslog', '--dc', 'on', '--func', 'off'],
	socket      : ['fsconf', '--socket-file'],
	prostart    : ['fsprostart'],
	sysvar      : ['fssymbol'],
	userproreset: ['fssetuserpro', '--clear'],
};
const t73api = {
	pwd         : ['kpwd'],
	cd          : ['kcd'],
	stat        : ['kstat', '--system'],
	who         : ['kwho'],
	proset      : ['kproset'],
	proreset    : ['kproreset'],
	clear       : ['kclear'],
	logstart    : ['ksplogstart'],
	errlogstart : ['kerrlogstart'],
	log         : ['klog', '--dc', 'on', '--func', 'off'],
	socket      : ['kselectsocket'],
	prostart    : ['kprostart'],
	sysvar      : ['ksystemvariable'],
	userproreset: ['ksetuserpro', '--clear'],
};
const tapi = model() === 'T5831' ? t31api : t73api;
const openAPI = {};
Object.keys(tapi).forEach(key => {
	switch(key) {
		case 'sysvar':
			openAPI[key] = (keyVals) => __setSysVar(...tapi[key], ...keyVals);
			break;
		case 'socket':
			openAPI[key] = (...vals) => {
				const socVal = (model() === 'T5773' && isAsciiSoc(...vals))
					? [callSync('cat', ...vals).trim()]
					: vals;
				callSync(...tapi[key], ...socVal);
			};
			break;
		default:
			openAPI[key] = (...vals) => callSync(...tapi[key], ...vals);
			break;
	}
});
openAPI['sysid'] = () => model() === 'T5773' ? getSysIdT73() : getSysIdT31();

function callSync(cmd, ...args) {
	const proc = spawnSync(cmd, args);
	//console.log(proc.args.join(' '));
	if(proc.status === 0) return proc.stdout.toString();
	const msg = [
		proc.status,
		proc.error,
		proc.stderr.toString(),
		proc.stdout.toString()].join('\n');
	//const err = new Error(msg);
	//back(cmd);
	//throw new Error(cmd);
}

function __setSysVar (cmd, ...keyVals) {
	let flat = [];
	keyVals.forEach(kv => flat.push('--add', ...kv));
	if(flat.length === 0) return '';
	return callSync(cmd, ...flat);
}

async function getSysIdT73() {
	return new Promise(resolve => {
		const proc = spawn('oUTD_system_configuration');
		proc.stdout.on('data', data => {
			const lines = data.toString().split('\n').filter(line => line.match(/(MACHINE|SYSTEM) NAME .*= .*/));
			if(lines.length === 0) return;
			resolve(lines.map(msg => msg.split('=')[1].trim()).join('|'));
			proc.kill('SIGINT');
		});
	});
}

async function getSysIdT31() {
	return new Promise(resolve => {
		const proc = spawn('oUTD_SystemConf');
		proc.stdout.on('data', data => {
			const lines = data.toString().split('\n').filter(line => line.match(/(PRODUCT TYPE|MACHINE NAME) .*: .*/));
			if(lines.length === 0) return;
			resolve(lines.map(msg => msg.split(':')[1].trim()).join('|'));
			proc.kill('SIGINT');
		});
		proc.stdin.write('1\nR\nQ\n');
	});
}


Object.assign(module.exports, openAPI);
module.exports.model = model;
module.exports.callSync = callSync;
module.exports.isTesting = () => module.exports.stat().includes('TESTING');
module.exports.isOff = () => module.exports.who().includes('not used');
