const {execSync} = require('child_process');
const fs = require('fs');
const path = require('path');

function parse(msg = '') {
	const lineExp = /SYSTEMDUT[0-9]+\s*=\s*SITE[0-9]+\s*,\s*DUT[0-9]+/;
	const obj = {};
	msg.split('\n')
		.filter(line => line.match(lineExp))
		.forEach(line => {
			const trimmed = line.match(lineExp)[0];
			const [sysd, sited] = trimmed.split('=');
			obj[sited.replace(/[ \t]+/g, '')] = sysd.replace('SYSTEMDUT', '').trim();
		})
	if(Object.keys(obj).length === 0) {
		console.error('Please check socket file content');
		process.exit(1);
	}
	return obj;
}

function toSiteDutString(duts = []) {
	const keys = [];
	duts.forEach(msg => {
		const [dutExp, site = 1] = msg.split(':');
		if(! isNaN(dutExp)) {
			keys.push(`SITE${site},DUT${dutExp}`);
			return;
		}
		const [dutLow, dutHigh] = dutExp.split('-');
		if(dutLow && dutHigh) {
			const low = Math.min(dutLow, dutHigh);
			const high = Math.max(dutLow, dutHigh);
			for(let dut = low; dut <= high; dut++) {
				keys.push(`SITE${site},DUT${dut}`);
			}
			return;
		} 
		throw new Error(`${msg} is wrong`);
	});
	return keys;
}

function getSocFiles() {
	const files = fs.readdirSync('.')
		.filter(file => file.endsWith('.asc') || file.endsWith('.soc'))
		.filter(file => fs.readFileSync(file).toString().match(/^SOCKET\b/));
	if(files.length === 0) {
		throw new Error(`No ascii socket file under ${path.basename(process.env.PWD)}/`);
	}
	return files;
}

function help() {
	const cmdL = `${path.basename(process.argv[0])} ${path.basename(process.argv[1])}`;
	console.error(`Usage:
  wudut 2 7     # SITE1,DUT2 and SITE1,DUT7
  wudut 1-3     # SITE1,DUT1 and SITE1,DUT2 and SITE1,DUT3
  wudut 2-3:5   # SITE5,DUT2 and SITE5,DUT3
`);
}

function enableDuts(sysd) {
	const model = process.env['ATKEITMODEL'] || process.env['ATFSTMODEL'];
	const dutCmd = model === 'T5773' ? 'ksdut' : 'fssdut';
	const cmds = `${dutCmd} --disable-all; ${dutCmd} --enable ${sysd}`;
	execSync(cmds);
	console.log(cmds);
}

///////////////////////////////////////////////////////////////////////////////
if(process.argv.length <= 2) {
	help();
	process.exit(1);
}

const [,,...duts]  = process.argv;
const keys = toSiteDutString(duts);
const [socFile] = getSocFiles();	console.log(socFile);
const dict = parse(fs.readFileSync(socFile).toString()); //console.log(dict);
const sysd = keys.map(s => {
	if(! dict[s]) throw new Error(`Can not map "${s}" to SYSTEMDUT#`);
	return dict[s];
});
enableDuts(sysd);
console.log(keys);

