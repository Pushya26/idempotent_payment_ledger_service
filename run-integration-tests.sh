#!/bin/bash

# Idempotent Payment Ledger Service - Integration Test Runner
# This script helps run integration tests with docker-compose

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Idempotent Payment Ledger - Integration Test Runner ===${NC}"
echo

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null && ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}Warning: Docker not found. Integration tests require docker-compose.${NC}"
    exit 1
fi

# Start docker-compose services
echo -e "${BLUE}Starting Docker Compose services...${NC}"
docker-compose up -d

# Wait for services to be ready
echo -e "${BLUE}Waiting for services to be ready...${NC}"
for i in {1..30}; do
    if nc -z localhost 5432 2>/dev/null && nc -z localhost 6379 2>/dev/null; then
        echo -e "${GREEN}Services are ready!${NC}"
        break
    fi
    echo "Waiting... ($i/30)"
    sleep 1
done

echo

# Run integration tests
echo -e "${BLUE}Running integration tests...${NC}"
./mvnw verify -Pintegration

# Capture test result
TEST_RESULT=$?

echo

# Show summary
if [ $TEST_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ Integration tests passed!${NC}"
else
    echo -e "${YELLOW}✗ Integration tests failed.${NC}"
fi

# Option to stop services
echo
read -p "Stop Docker Compose services? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}Stopping Docker Compose services...${NC}"
    docker-compose down
    echo -e "${GREEN}Services stopped.${NC}"
fi

exit $TEST_RESULT
