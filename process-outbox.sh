#!/bin/sh

# Get token from Keycloak
TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_ISSUER_URI}/protocol/openid-connect/token" \
  -H "Content-Typeadmi: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=${KEYCLOAK_CLIENT_ID}" \
  -d "client_secret=${KEYCLOAK_CLIENT_SECRET}")

# Extract token
TOKEN=$(echo $TOKEN_RESPONSE | sed 's/.*"access_token":"\([^"]*\)".*/\1/')

if [ -z "$TOKEN" ]; then
  echo "Failed to obtain token from Keycloak"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
fi

# Call process-outbox endpoint with token
RESPONSE=$(curl -s -X POST "${HERMES_HOST}/hermes-admin/v1/process-all-outboxes" \
  -H "Authorization: Bearer ${TOKEN}" \
  -w "\nHTTP_STATUS:%{http_code}")

HTTP_STATUS=$(echo "$RESPONSE" | grep HTTP_STATUS | cut -d':' -f2)
BODY=$(echo "$RESPONSE" | sed '/HTTP_STATUS/d')

echo "Process outbox HTTP status: $HTTP_STATUS"
if [ "$HTTP_STATUS" != "200" ]; then
  echo "Failed API response: $BODY"
  exit 1
fi
