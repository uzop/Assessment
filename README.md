# Assessment

#  Messaging App

A serverless WebSocket-based messaging application for one-to-one chats, built with AWS Lambda, API Gateway WebSocket, DynamoDB, Spring Boot DI, and AWS SAM. Users can register, log in, connect via WebSocket, and exchange messages in real-time.

## Features
- **REST Endpoints**:
  - `/register`: Create a user account.
  - `/login`: Authenticate and receive a JWT.
- **WebSocket Actions**:
  - `$connect`: Authenticate with JWT and store connection.
  - `$disconnect`: Remove connection.
  - `sendMessage`: Send a message to another user.
- **Storage**:
  - DynamoDB tables: `Connections` (WebSocket connections), `Users` (user credentials).
- **Security**:
  - JWT-based authentication (`my-test-jwt-secret-key-1234567890abcdef`).

## Prerequisites
- **Java 17**: For building and running.
- **Maven 3.8+**: For dependency management.
- **AWS CLI**: Configured with credentials (`aws configure`).
- **SAM CLI**: For deploying serverless resources.
- **wscat**: For WebSocket testing (`npm install -g wscat`).
- **AWS Account**: With permissions for Lambda, API Gateway, DynamoDB, IAM.
- **OS**: Linux/MacOS/Windows (commands assume Unix-like shell).

Install AWS CLI

Configure AWS CLI:
aws configure

Set Access Key, Secret Key, region (e.g., us-east-1), and output format (json).
Verify SAM CLI:
sam --version


Building and Running Tests

Build the Project:
mvn clean package
Creates target/assessment-1.0-SNAPSHOT.jar.

Run Tests (Optional):
mvn test

To debug, run:
mvn test -X > test-output.txt


##Deploying to AWS

Package the Application:
mvn clean package 

Deploy with SAM:
sam deploy --guided

Stack Name: e.g., websocket-chat-app.
Region: e.g., us-east-1.
Capabilities: CAPABILITY_IAM, CAPABILITY_NAMED_IAM.
Follow prompts to configure (accept defaults for most).
Outputs:
RestApiId: For /register, /login.
WebSocketApiId: For WebSocket connections.
Note Outputs:
After deployment, check Outputs in the AWS CloudFormation console or CLI:
aws cloudformation describe-stacks --stack-name websocket-chat-app --query "Stacks[0].Outputs"

Save RestApiId and WebSocketApiId.
Testing the Application

1. Test REST Endpoints
Register a User:

curl -X POST https://<RestApiId>.execute-api.<region>.amazonaws.com/prod/register \
  -H "Content-Type: application/json" \
  -d '{"username": "userA", "password": "pass"}'
Expected: {"message": "User registered successfully"}


Register Another User:
curl -X POST https://<RestApiId>.execute-api.<region>.amazonaws.com/prod/register \
  -H "Content-Type: application/json" \
  -d '{"username": "userB", "password": "pass"}'
  
Login (Get JWT):
curl -X POST https://<RestApiId>.execute-api.<region>.amazonaws.com/prod/login \
  -H "Content-Type: application/json" \
  -d '{"username": "userA", "password": "pass"}'
Expected: {"token": "<jwt-userA>"}
Save <jwt-userA> for WebSocket.

Login for userB:
curl -X POST https://<RestApiId>.execute-api.<region>.amazonaws.com/prod/login \
  -H "Content-Type: application/json" \
  -d '{"username": "userB", "password": "pass"}'
Save <jwt-userB>.

2. Test WebSocket Connections
Connect as userA:
wscat -c "wss://<WebSocketApiId>.execute-api.<region>.amazonaws.com/prod?token=<jwt-userA>"
Should connect successfully.

Connect as userB (in a new terminal):
wscat -c "wss://<WebSocketApiId>.execute-api.<region>.amazonaws.com/prod?token=<jwt-userB>"

Send Message from userA to userB: In userA’s wscat session:
json

{"action": "sendMessage", "message": "Hi userB!", "recipient": "userB"}
Expected: userB’s wscat receives "Hi userB!".
Send Message from userB to userA: In userB’s wscat session:
json
{"action": "sendMessage", "message": "Hi userA!", "recipient": "userA"}
Expected: userA’s wscat receives "Hi userA!".

Disconnect:
Type Ctrl+C in each wscat session to disconnect.
