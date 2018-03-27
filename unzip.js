const { spawn, spawnSync } = require('child_process');
const { callSync } = require('./tester.js');

module.exports.untgz = (zipFile, dstDir) => {
	return new Promise(resolve => {
		const unzip = spawn('unzip', ['-p', zipFile]);
		if(dstDir) callSync('mkdir', '-p', dstDir);
		const tarOpt = dstDir ? ['xzf', '-', '-C', dstDir] : ['xzf', '-'];
		const untar = spawn('tar', tarOpt);
		untar.on('close', code => {
			if(code === 0) resolve();
			else reject(code);
		});
		unzip.stdout.pipe(untar.stdin);
		console.log(`Extracting ${zipFile}`);
	});
}

module.exports.tpNameSync = zipFile => {
	const stdout = callSync('unzip', '-t', zipFile);
	const result = stdout.match(/\b\w+.tgz\b/);
	if(result) return result[0].replace(/\.tgz/g, '');
	throw Error(`Can not find *.tgz in ${tpZip}`);
}

