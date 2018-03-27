const { spawn } = require('child_process');
const tester = require('./tester.js');
const { zcnt } = require('./zcnt.js');
const redis = require('redis');
const os = require('os');
const ifaces = () => os.networkInterfaces();

module.exports.back = back;
async function back	(...msg) {
	const client = redis.createClient(6379, '10.71.32.138');
	client.on("error", err => {
		//console.log(err);
		client.quit();
		return;
	});
	const getTime = () => new Date().toLocaleString();
	const name = `${await tester.sysid()}|${ifaces().eth0[0].address}`;
	const key = `wuf:log`;
	return new Promise(resolve => client.rpush(key, 
		[getTime(), name, process.env.PWD, process.argv.slice(2).join(' '), ...msg].join('\n'),
		(err, val) => {
			resolve();
			client.quit();
		}));
}


module.exports.cntr = cntr;
async function cntr() {
	const client = redis.createClient(6379, '10.71.32.138');
	client.on("error", err => {
		//console.log(err);
		client.quit();
		return;
	});
	const key = 'wuf:cnt';
	const field = `${await tester.sysid()}`;
	client.hsetnx(key, field, 0);
	client.hincrby(key, field, 1, (err, val) => client.quit());
	zcnt('wuf:zcnt');
}

module.exports.backRTE = backRTE;
function backRTE(fileBaseName, ...types) {
	const fullRteName = (type) => tester.model() === 'T5831'
		? `${fileBaseName}-fsdiag_1-Stn1-${type}.txt`
		: `${fileBaseName}-kei_1-Stn1-${type}.txt`;
	const procRte = spawn('tail', ['-F', ...types.map(type => fullRteName(type))]);
	procRte.stdout.on('data', data => {
		//console.log(data.toString());
		console.log('\x1b[31m\x1b[1m', data.toString() ,'\x1b[0m');
		back(data.toString());
		setTimeout(() => {
			console.log(`ERROR`);
			process.kill(procRte.pid);
			process.exit(1);
		} , 1500);
	});
}
