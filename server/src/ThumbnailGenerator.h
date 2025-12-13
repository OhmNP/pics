#pragma once

#include <string>

class ThumbnailGenerator {
public:
  // Generate a thumbnail for the given image
  // Returns true on success, false on failure
  static bool generateThumbnail(const std::string &inputPath,
                                const std::string &outputPath,
                                int maxWidth = 300, int maxHeight = 300);

  // Get the thumbnail path for a given photo ID
  static std::string getThumbnailPath(int photoId);

  // Check if a thumbnail exists for a given photo ID
  static bool thumbnailExists(int photoId);

  // Ensure the thumbnails directory exists
  static bool ensureThumbnailsDirectory();

private:
  static const std::string THUMBNAILS_DIR;
};
