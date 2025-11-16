#!/bin/bash

echo "Initializing LocalStack S3..."

# AWS CLI를 사용하여 S3 버킷 생성
awslocal s3 mb s3://coupon-upload-bucket --region us-east-1

# 버킷 목록 확인
awslocal s3 ls

echo "LocalStack S3 initialization complete!"
