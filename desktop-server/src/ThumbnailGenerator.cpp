#include "ThumbnailGenerator.h"
#include "Logger.h"
#include <filesystem>
#include <fstream>

// Define STB_IMAGE implementation
#define STB_IMAGE_IMPLEMENTATION
#include "external/stb_image.h"

#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include "external/stb_image_resize2.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "external/stb_image_write.h"

const std::string ThumbnailGenerator::THUMBNAILS_DIR = "./storage/thumbnails";

bool ThumbnailGenerator::generateThumbnail(const std::string &inputPath,
                                           const std::string &outputPath,
                                           int maxWidth, int maxHeight) {
  // Load the image
  int width, height, channels;
  unsigned char *imageData =
      stbi_load(inputPath.c_str(), &width, &height, &channels, 0);

  if (!imageData) {
    LOG_ERROR("Failed to load image: " + inputPath);
    return false;
  }

  // Calculate thumbnail dimensions while maintaining aspect ratio
  int thumbWidth, thumbHeight;
  if (width > height) {
    thumbWidth = maxWidth;
    thumbHeight = (int)((float)height / width * maxWidth);
  } else {
    thumbHeight = maxHeight;
    thumbWidth = (int)((float)width / height * maxHeight);
  }

  // Ensure dimensions are at least 1
  if (thumbWidth < 1)
    thumbWidth = 1;
  if (thumbHeight < 1)
    thumbHeight = 1;

  // Allocate memory for thumbnail
  unsigned char *thumbnailData =
      (unsigned char *)malloc(thumbWidth * thumbHeight * channels);
  if (!thumbnailData) {
    LOG_ERROR("Failed to allocate memory for thumbnail");
    stbi_image_free(imageData);
    return false;
  }

  // Resize the image
  if (!stbir_resize_uint8_linear(imageData, width, height, 0, thumbnailData,
                                 thumbWidth, thumbHeight, 0,
                                 (stbir_pixel_layout)channels)) {
    LOG_ERROR("Failed to resize image: " + inputPath);
    free(thumbnailData);
    stbi_image_free(imageData);
    return false;
  }

  // Write the thumbnail as JPEG
  int result = stbi_write_jpg(outputPath.c_str(), thumbWidth, thumbHeight,
                              channels, thumbnailData, 85);

  // Clean up
  free(thumbnailData);
  stbi_image_free(imageData);

  if (!result) {
    LOG_ERROR("Failed to write thumbnail: " + outputPath);
    return false;
  }

  LOG_DEBUG("Generated thumbnail: " + outputPath + " (" +
            std::to_string(thumbWidth) + "x" + std::to_string(thumbHeight) +
            ")");
  return true;
}

std::string ThumbnailGenerator::getThumbnailPath(int photoId) {
  return THUMBNAILS_DIR + "/" + std::to_string(photoId) + ".jpg";
}

bool ThumbnailGenerator::thumbnailExists(int photoId) {
  std::string path = getThumbnailPath(photoId);
  return std::filesystem::exists(path);
}

bool ThumbnailGenerator::ensureThumbnailsDirectory() {
  try {
    if (!std::filesystem::exists(THUMBNAILS_DIR)) {
      std::filesystem::create_directories(THUMBNAILS_DIR);
      LOG_INFO("Created thumbnails directory: " + THUMBNAILS_DIR);
    }
    return true;
  } catch (const std::exception &e) {
    LOG_ERROR("Failed to create thumbnails directory: " +
              std::string(e.what()));
    return false;
  }
}
