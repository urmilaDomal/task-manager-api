#!/bin/bash
# One-time setup: creates OIDC trust between GitHub Actions and AWS
# Run this ONCE locally before pushing to GitHub
# Replace values below with your real GitHub username and repo name

GITHUB_USERNAME="YOUR_GITHUB_USERNAME"
REPO_NAME="task-manager-api"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "Setting up OIDC for GitHub Actions → AWS"
echo "Account: $ACCOUNT_ID"
echo "Repo: $GITHUB_USERNAME/$REPO_NAME"

# Step 1: Create OIDC provider
echo ""
echo "Step 1: Creating GitHub OIDC provider..."
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 \
  2>/dev/null || echo "OIDC provider already exists — skipping"

# Step 2: Create IAM role
echo ""
echo "Step 2: Creating IAM deploy role..."
aws iam create-role \
  --role-name GitHubActionsDeployRole \
  --assume-role-policy-document "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [{
      \"Effect\": \"Allow\",
      \"Principal\": {
        \"Federated\": \"arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com\"
      },
      \"Action\": \"sts:AssumeRoleWithWebIdentity\",
      \"Condition\": {
        \"StringEquals\": {
          \"token.actions.githubusercontent.com:aud\": \"sts.amazonaws.com\"
        },
        \"StringLike\": {
          \"token.actions.githubusercontent.com:sub\": \"repo:${GITHUB_USERNAME}/${REPO_NAME}:*\"
        }
      }
    }]
  }"

# Step 3: Attach permissions
echo ""
echo "Step 3: Attaching permissions..."
for POLICY in \
  AWSCloudFormationFullAccess \
  AmazonS3FullAccess \
  AWSLambda_FullAccess \
  IAMFullAccess \
  AmazonAPIGatewayAdministrator \
  AmazonDynamoDBFullAccess \
  CloudWatchFullAccess \
  AmazonSNSFullAccess \
  AmazonCognitoPowerUser; do
  aws iam attach-role-policy \
    --role-name GitHubActionsDeployRole \
    --policy-arn arn:aws:iam::aws:policy/$POLICY
  echo "Attached: $POLICY"
done

# Step 4: Print role ARN
ROLE_ARN=$(aws iam get-role \
  --role-name GitHubActionsDeployRole \
  --query 'Role.Arn' --output text)

echo ""
echo "══════════════════════════════════════════════════════════"
echo "✅ Setup complete!"
echo ""
echo "Add this as a GitHub Secret named: AWS_DEPLOY_ROLE_ARN"
echo ""
echo "Role ARN: $ROLE_ARN"
echo ""
echo "GitHub repo → Settings → Secrets and variables → Actions"
echo "→ New repository secret → Name: AWS_DEPLOY_ROLE_ARN"
echo "══════════════════════════════════════════════════════════"
