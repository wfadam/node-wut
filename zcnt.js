const redis = require('redis');

async function hgetall(key) {
	return new Promise((resolve, reject) => {
		const client = redis.createClient(6379, '10.71.32.138');
		client.hgetall(key, handler(client, resolve, reject));
	});
}

function handler(client, resolve, reject) {
	return (err, data) => {
		if(err) {
			reject(err);
		} else {
			resolve(data);
			client.quit();
		}
	};
}

function paramsOfZadd(obj) {
	let args = []
	Object.keys(obj).forEach(key => {
		args.push(obj[key]);
		args.push(key);
	});
	return args;
}

exports.zcnt = zcnt;
async function zcnt	(key) {
	const kv = await hgetall('wuf:cnt');
	return new Promise((resolve, reject) => {
		const client = redis.createClient(6379, '10.71.32.138');
		client.zadd(key, ...paramsOfZadd(kv),  handler(client, resolve, reject));
	});
}

