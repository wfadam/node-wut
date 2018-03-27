process.on('unhandledRejection', (reason, p) => console.log(reason));
process.on('uncaughtException', (reason, p) => console.log(reason));

const fs = require('fs');
const querystring = require('querystring');
const path = require('path');
const tester = require('./tester.js');
const {back, backRTE, cntr} = require('./log.js');
const log = require('./log.js');
const logDir = 'datalogs/';
const lastLine = (msg = '') => msg.trim().split('\n').pop();

function help() {
	const msg = `Usage:\n\twut <LogFileBaseName> [TestBlockName] ...`;
	errOut(`Usage:\n\twut <LogFileBaseName> [TestBlockName] ...`);
}

function errOut(msg = '') {
	console.log(msg + '\nQuit');
	process.exit(1);
}

function checkAgent() {
	const AGENT_PATH = 'javaapi/Agent.class';
	if(! fs.existsSync(AGENT_PATH)) {
		errOut(`Can not find ${AGENT_PATH}`);
	}
}

function checkTester() {
	if(tester.isTesting() && ! isOwnWork()) {
		errOut(`Tester is already running under ${lastLine(tester.pwd())}`);
	}
	if(tester.isOff()) {
		errOut(`Please ${tester.model() === 'T5831' ? 'startfs' : 'startk'}`);
	}
}

function parseArgs() {
	const cmdL = process.argv;
	const logName = cmdL[2];
	const tbArr = cmdL.slice(3).filter(s => ! s.endsWith('_tp.zip') && ! s.startsWith('ECOTS_'));
	const flows = ["Debug"];
	const [tpZip] = cmdL.filter(opt => opt.match(/^.*_tp.zip$/));
	const keyVals = cmdL.filter(opt => opt.match(/^ECOTS_.*=\w*$/)).map(kv => kv.split('='));
	return {flows, tpZip, keyVals, logName, tbArr};
}

function setupCPNL(tXXXXXX, tpFullName) {
	const classFileName = tester.model() === 'T5831' 
		? `javaapi/${tpFullName}.class` 
		: `javaapi.${tpFullName}.class`;
	const socFileName = `${tXXXXXX}/${tpFullName}.soc`;
	tester.cd(tXXXXXX);
	tester.proset('javaapi.Agent.class');
	tester.socket(socFileName);
	setTpClass(classFileName);
}

function enableLog(dir, flow, tpFullName, logName) {
	tester.callSync('mkdir', '-p', dir);
	const timeStr = new Date().toString().replace(/ (20\d\d) /, '_').replace(/ GMT.*$/, '').replace(/[ :]/g, '');
	tester.log();
	if(logName.length === 0){
		tester.logstart(dir, `${flow}_${tpFullName}_${timeStr}`);
	}else {
		tester.logstart(dir, `${logName}`);
	}
	tester.errlogstart(dir, `.${flow}_${tpFullName}_${timeStr}`);
	const baseName = `${process.env.PWD}/${dir}/.${flow}_${tpFullName}_${timeStr}`;
	backRTE(baseName, 'RTE', 'System-err', 'S0001-err', 'S0005-err');
}

function setFlow(flow) {
	tester.sysvar([['ECOTS_SD_STEP', "Debug"], ['ECOTS_SD_RESCREEN', 0], ['ECOTS_SD_DATALOGDISP', 'ON']]);
}

function setTpClass(name) {
	tester.sysvar([['ECOTS_SD_TPCLASS', name]]);
}

function setFlowItems(items) {
	const val = items.join(';');
	tester.sysvar([['ECOTS_SD_QUEUE', val]]);
}

function tpName(dir) {
	const socFiles = fs.readdirSync(dir).filter(file => file.endsWith('.soc'));
	let err;
	switch(socFiles.length) {
		case 1: return socFiles[0].replace(/.soc$/g, '');
		case 0: err = new Error(`Can not find .soc file under ${dir}`); break;
		default: err = new Error(`More than one .soc file found under ${dir}`); break;
	}
	back(err.stack);
	throw err;
}

function isOwnWork() {
	const srcDir = process.env.PWD;
	const dstDir = srcDir.replace('/nfsusers/', '/sandbox/');
	return lastLine(tester.pwd()) === dstDir;
}

function sync() {
	const srcDir = process.env.PWD;
	const dstDir = srcDir.replace('/nfsusers/', '/sandbox/');
	if(srcDir === dstDir) return dstDir;

	console.log(`Syncing to ${dstDir}`);
	tester.callSync('mkdir', '-p', dstDir);
	tester.callSync('rsync', '-az', '--delete', '--force', '--exclude=*5831', '--exclude=*5773', '--exclude=saveflows/', '--exclude=datalogs/', '--exclude=*.java', '--exclude=*.asc', '--exclude=*.prep', '--exclude=.svn/', srcDir, path.dirname(dstDir));
	return dstDir;
}

function tpModified() {
	const srcDir = process.env.PWD;
	const dstDir = srcDir.replace('/nfsusers/', '/sandbox/');
	const msg = tester.callSync('rsync', '-nvr', '--exclude=*5831', '--exclude=*5773', '--exclude=saveflows/', '--exclude=datalogs/', '--exclude=*.java', '--exclude=*.asc', '--exclude=*.prep', '--exclude=.svn/', srcDir, path.dirname(dstDir));
	const basename = path.basename(srcDir);
	const modFiles = msg.split('\n').filter(s => s.includes(basename));
	const isMod = modFiles.length > 0;
	console.log(isMod ? `\nModified files: ${modFiles}\n` : '');
	return isMod;
}

async function extract(tpZip) {
	const unzip = require('./unzip.js');
	const tDir = unzip.tpNameSync(tpZip).slice(0, 7);
	const srcDir = `${process.env.PWD}/${tDir}`;
	const dstDir = srcDir.replace('/nfsusers/', '/sandbox/');
	await unzip.untgz(tpZip, path.dirname(dstDir));
	return dstDir;
}

async function run() {
	cntr();
	await back();
	if(! tester.isTesting() && tpModified()) {
		tester.proreset();
		tester.userproreset();
		tester.clear();
	}

	const {flows, tpZip, keyVals, logName, tbArr} = parseArgs();
	const dstDir = tpZip ? await extract(tpZip) : sync();
	const tpFullName = tpName(dstDir);

	setupCPNL(dstDir, tpFullName);
	console.log(tester.sysvar(keyVals));

	for(let flow of flows) {
		setFlow(flow);
		setFlowItems(tbArr);
		enableLog(logDir, flow, tpFullName, logName);

		console.log(`Running \n\t${tbArr.join('\n\t')}`);
		tester.prostart();
	}
	process.exit(0);
}

/************************** Execution starts here *****************************/
if(process.argv.length < 4) {
	help();
}
checkTester();
checkAgent();
run();

