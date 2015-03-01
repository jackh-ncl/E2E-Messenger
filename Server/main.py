import os, sys, json, base64, logging, re
from google.appengine.ext import db
from google.appengine.api import memcache

# sys.path includes 'server/lib' due to appengine_config.py
from flask import Flask
from flask import request
from flask import render_template
from passlib.hash import pbkdf2_sha256
import requests

GCM_KEY = 'XXXXXXXXXXXXXXXXXXXXXXXX'
RECAPTCHA_KEY = 'XXXXXXXXXXXXXXXXXXXXXXXX'
app = Flask(__name__)
#logging.getLogger().setLevel(logging.DEBUG)

Participant = {
	'alice': 0,
	'bob': 1
}

MessageType = {
	'key_exchange': 0,
	'message': 1,
	'reauth': 2,
	'error': 3
}

MessageErrorType = {
	'not_registered': 0,
	'unauthorised': 1,
	'user_not_exists': 2,
	'send_limit': 3,
	'invalid_message_type': 4,
	'validation': 5,
	'gcm': 6
}

RegistrationErrorType = {
	'reauth': 0,
	'exists': 1,
	'validation': 2,
	'recaptcha': 3,
	'password': 4
}

class User(db.Model):
	username = db.StringProperty(required=True)
	device_id = db.StringProperty(required=True)
	session_key = db.StringProperty(required=True)
	password = db.StringProperty(required=True)

def rateLimit(sender):
	sent_count = memcache.get(sender.username)
	if sent_count is not None:
		if sent_count < 3600:
			memcache.incr(sender.username)
		else:
			return json.dumps({'success': 0, 'error_type': MessageErrorType['send_limit']})
	else:
		memcache.set(key=sender.username, value=1, time=3600)

def validUsername(username):
	return re.match('^(?!.*[_-]{2})[a-z0-9_-]{3,15}$', username) != None

def validDeviceID(device_id):
	return re.match('^[a-zA-Z0-9_-]{1,}$', device_id) != None and sys.getsizeof(device_id) < 4096

def validBase64(base64Str):
	return re.match('^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{4})$', base64Str) != None

def validPassword(password):
	return len(password) > 5 and len(password) <= 64

def validReCaptcha(remote_ip, recaptcha, challenge):
	url = 'http://www.google.com/recaptcha/api/verify'

	data = {
		'privatekey': RECAPTCHA_KEY,
		'remoteip': remote_ip,
		'challenge': challenge,
		'response': recaptcha
	}

	#print data

	r = requests.post(url, data=data, headers={'Content-Type': 'application/x-www-form-urlencoded'})

	if r.status_code != 200:
		return 	False

	#print r.text

	resp = r.content.split("\n")

	if resp[0] == 'false':
		#print 'Invalid CAPTCHA:', resp[1]
		return False
	else:
		#print 'VALID CAPTCHA'
		return True

@app.route('/register', methods=['POST'])
def register():
	data = request.get_json()

	try:
		username = str(data['username'].lower().strip())
		device_id = str(data['device_id'])
		session_key = str(data['session_key'])
		recaptcha = str(data['recaptcha'])
		challenge = str(data['challenge_key'])
		password = str(data['password'])
	except:
		#print "error!"
		return json.dumps({'success': 0, 'error_type': RegistrationErrorType['validation']})

	if not validUsername(username) or not validDeviceID(device_id): 
		return json.dumps({'success': 0, 'error_type': RegistrationErrorType['validation']})

	u_test = User.all().filter('username =', username).get()
	did_test = User.all().filter('device_id =', device_id).get()

	if u_test is None and did_test is None: # username and device ID does not exist

		if not validPassword(password):
			#print 'invalid password'
			return json.dumps({'success': 0, 'error_type': RegistrationErrorType['validation']})

		if not validReCaptcha(request.remote_addr, recaptcha, challenge):
			return json.dumps({'success': 0, 'error_type': RegistrationErrorType['recaptcha']})

		session_key = generate_session_key()

		# hash password
		pass_hash = pbkdf2_sha256.encrypt(password, rounds=2000, salt_size=16)

		# store user in database
		u = User(username=username, device_id=device_id, session_key=session_key, password=pass_hash)
		u.put()
		return json.dumps({'success': 1, 'session_key': session_key})
	elif did_test is not None and (u_test is None or did_test.device_id == u_test.device_id): # user re-registers on same device			 

		#if did_test.password != password:
		#	return json.dumps({'success': 0, 'error_type': RegistrationErrorType['password']})

		#if not validPassword(password):
		#	return json.dumps({'success': 0, 'error_type': RegistrationErrorType['validation']})

		if did_test.session_key != session_key:
			return json.dumps({'success': 0, 'error_type': RegistrationErrorType['reauth']})
		else:
			#logging.debug('changing username from ' + did_test.username + ' to ' + username)
			# hash password and update user
			did_test.username = username
			#pass_hash = pbkdf2_sha256.encrypt(password, rounds=2000, salt_size=16)
			#did_test.password = pass_hash
			did_test.put()
			return json.dumps({'success': 1, 'session_key': session_key})
	elif u_test is not None:

		if not validPassword(password):
			return json.dumps({'success': 0, 'error_type': RegistrationErrorType['validation']})
		
		if not validReCaptcha(request.remote_addr, recaptcha, challenge):
			return json.dumps({'success': 0, 'error_type': RegistrationErrorType['recaptcha']})

		if pbkdf2_sha256.verify(password, u_test.password):
			# Remove current entry for an existing device ID
			if did_test is not None:
				did_test.delete()

			# Change device ID of user and generate/return new session key
			u_test.device_id = device_id
			session_key = generate_session_key()
			u_test.session_key = session_key

			u_test.put()
			return json.dumps({'success': 1, 'session_key': session_key})
		else:
			return json.dumps({'success': 0, 'error_type': RegistrationErrorType['exists']})
	else:
		return json.dumps({'success': 0, 'error_type': RegistrationErrorType['exists']})


def generate_session_key(num_bytes=32):
	return base64.b64encode(os.urandom(num_bytes))

def sendToGCM(data):
	url = 'https://android.googleapis.com/gcm/send'
	headers = {
		'Content-type': 'application/json',
		'Authorization': 'key=' + GCM_KEY
	}

	# validate input and payload size
	if len( json.dumps(data['data']) ) > 4096:
		return json.dumps({'success': 0, 'error_type': MessageErrorType['validation']})

	#print 'sending to gcm'
	r = requests.post(url, data=json.dumps(data), headers=headers)
	
	# if GCM request was not successful
	# NOTE: must handle errors for each status code, 400, 401, 500+, etc.
	if r.status_code != 200:
		return json.dumps({'success': 0, 'error_type': MessageErrorType['gcm']})
	#print r.text
	resp = r.json()

	# check if registered GCM device ID has changed
	# and update if necessary 
	if resp['canonical_ids'] != 0:
		new_did = resp['results'][0]['registration_id']
		user = User.all().filter('device_id =', data['registration_ids'][0]).get()
		user.device_id = new_did
		user.put

	if resp['success'] == 1:
		return json.dumps({'success': 1})
	else:
		return json.dumps({'success': 0, 'error_type': MessageErrorType['gcm']})

def reauth(data):
	# (limit this action once per x mins)
	username = str(data['username'].lower())
	device_id = str(data['device_id'])

	did_test = User.all().filter('device_id =', device_id).get()
	u_test = User.all().filter('username =', username).get()

	# if device is not registered
	if did_test is None:
		return json.dumps({'success': 0, 'error_type': MessageErrorType['not_registered']})

	payload = getReauthorisationData(username, device_id, did_test.session_key, data['register'])
	return sendToGCM(payload)

def validMessageType(message_type):
	return message_type in MessageType.values()

def validMessage(message):
	return validBase64(message)

def validIV(IV):
	return validBase64(IV)

def representsInt(s):
    try: 
        int(s)
        return True
    except ValueError:
        return False

@app.route('/send', methods=['POST']) #requires: from, to, message and [session id]
def send():
	data = request.get_json()

	message_type = data['message_type']

	if not validMessageType(message_type):
		return json.dumps({'success': 0, 'error_type': MessageErrorType['invalid_message_type']})

	if message_type == MessageType['reauth']:
		return reauth(data);

	from_user = str(data['from'].lower())
	to_user = str(data['to'].lower())
	session_key = str(data['session_key'])

	# check input data is valid
	if from_user == to_user or not validUsername(from_user) or not validUsername(to_user):
		return json.dumps({'success': 0, 'error_type': MessageErrorType['validation']})

	sender = User.all().filter('username =', from_user).get()
	recipient = User.all().filter('username =', to_user).get()

	# check sender exists
	if sender is None:
		return json.dumps({'success': 0, 'error_type': MessageErrorType['not_registered']})

	#logging.debug('stored key = ' + sender.get().session_key)
	#logging.debug('sent key = ' + data['session_key'])

	# check session key is valid
	if sender.session_key != session_key:
		# must reauthorise
		return json.dumps({'success': 0, 'error_type': MessageErrorType['unauthorised']})

	rateLimit(sender)

	# check recipient exists
	if recipient is None:
		return json.dumps({'success': 1})
		#return json.dumps({'success': 0, 'error_type': MessageErrorType['not_exists']}) #maybe remove error message to keep anonymity

	recipient_id = recipient.device_id

	# package up data to send
	post_data = None

	if message_type == MessageType['message']:
		post_data = getMessageData(data, recipient_id)
	elif message_type == MessageType['key_exchange']:
		post_data = getKeyExchangeData(data, recipient_id)
	elif message_type == MessageType['error']:
		post_data = getErrorData(data, recipient_id)
	
	if post_data is None:
		return json.dumps({'success': 0, 'error_type': MessageErrorType['validation']})

	# send data to GCM servers
	return sendToGCM(post_data)

def getMessageData(data, recipient_id):
	if representsInt(data['message_id']) and validMessage(data['message']) and validIV(data['IV']):
		return {
				'registration_ids': [recipient_id],
				'data': {
					'from_user': data['from'].lower(),
					'message_id': data['message_id'],
					'message': data['message'],
					'IV': data['IV'],
					'enigma_message_type': MessageType['message']
				}
			}
	else:
		#print 'invalid'
		return None

def getKeyExchangeData(data, recipient_id):
	#print data['participant']
	return {
			'registration_ids': [recipient_id],
			'data': {
				'from_user': data['from'].lower(),
				'participant': data['participant'],
				'round': data['round'],
				'payload': data['payload'],
				'enigma_message_type': MessageType['key_exchange']
			}
		}

def getErrorData(data, recipient_id):
	return {
			'registration_ids': [recipient_id],
			'data': {
				'from_user': data['from'].lower(),
				#'error_type': data['error_type'],
				'enigma_message_type': MessageType['error']
			}
		}

def getReauthorisationData(username, device_id, session_key, register):
	return {
			'registration_ids': [device_id],
			'data': {
				'username': username,
				'session_key': session_key,
				'enigma_message_type': MessageType['reauth'],
				'register': register
			}
		}

#@app.route('/')
#@app.route('/<name>')
#def hello(name=None):
#	return render_template('hello.html', name=name)
