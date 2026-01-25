#!/bin/bash

# Finstream K8s Deployment Configuration Helper
# This script substitutes AWS and environment variables into K8s YAML files

set -e

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "Error: .env file not found. Copy .env.example to .env and update with your values:"
    echo "  cp k8s/.env.example k8s/.env"
    echo "  # Edit k8s/.env with your AWS account ID and RDS endpoint"
    exit 1
fi

# Load environment variables from .env
export $(cat .env | grep -v '^#' | xargs)

# Validate required variables
if [ -z "$AWS_ACCOUNT_ID" ] || [ "$AWS_ACCOUNT_ID" = "YOUR_AWS_ACCOUNT_ID" ]; then
    echo "Error: AWS_ACCOUNT_ID not set in .env"
    exit 1
fi

if [ -z "$AWS_REGION" ]; then
    echo "Error: AWS_REGION not set in .env"
    exit 1
fi

if [ -z "$DB_HOST" ] || [[ "$DB_HOST" == *"XXXXXX"* ]]; then
    echo "Error: DB_HOST not set properly in .env"
    exit 1
fi

echo "Deploying with configuration:"
echo "  AWS_ACCOUNT_ID: $AWS_ACCOUNT_ID"
echo "  AWS_REGION: $AWS_REGION"
echo "  DB_HOST: $DB_HOST"
echo ""

# Create temp directory for processed files
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Process each YAML file
for file in base/*.yaml; do
    filename=$(basename "$file")
    envsubst < "$file" > "$TEMP_DIR/$filename"
    echo "Processed: $filename"
done

echo ""
echo "Processed files in: $TEMP_DIR"
echo ""
echo "To apply to cluster, run:"
echo "  kubectl apply -f $TEMP_DIR/"
echo ""
echo "Or to preview changes:"
echo "  kubectl diff -f $TEMP_DIR/"
