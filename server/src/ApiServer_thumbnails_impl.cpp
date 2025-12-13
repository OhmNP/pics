
#include "ApiServer.h"
#include "Logger.h"
#include "ThumbnailGenerator.h"
#include <filesystem>
#include <iostream>
#include <nlohmann/json.hpp>

using json = nlohmann::json;

namespace fs = std::filesystem;
using json = nlohmann::json;

std::string
ApiServer::handlePostRegenerateThumbnails(const crow::request &req) {
  try {
    std::string requestBody = req.body;
    std::string ipAddress = req.remote_ip_address;
    std::string authHeader = req.get_header_value("Authorization");
    std::string token = (authHeader.length() > 7) ? authHeader.substr(7) : "";
    AuthSession session = db_.getSessionByToken(token);
    int userId = session.userId;

    LOG_INFO("Regenerate thumbnails request: " + requestBody);
    auto requestData = json::parse(requestBody);

    if (requestData.contains("all") && requestData["all"].get<bool>()) {
      // Regenerate all logic - for now, we don't have a specific "get all IDs"
      // exposed easily without massive query. But we can iterate over cache
      // directory and delete everything strictly speaking, but that doesn't
      // respect the DB. Better: Iterate all cache files in thumbnails dir and
      // delete them.
      try {
        std::string thumbDir =
            "./storage/thumbnails"; // Matches ThumbnailGenerator.cpp

        if (fs::exists(thumbDir)) {
          for (const auto &entry : fs::directory_iterator(thumbDir)) {
            fs::remove(entry.path());
          }
        }

        // Always return success even if directory didn't match, as state is
        // "cleared"
        // Always return success even if directory didn't match, as state is
        // "cleared"
        return json({{"success", true},
                     {"message", "All thumbnails cleared. They will be "
                                 "regenerated on demand."}})
            .dump();

      } catch (const std::exception &e) {
        return json({{"error",
                      "Failed to clear cache: " + std::string(e.what())}})
            .dump();
      }
    }

    if (requestData.contains("photoId")) {
      int photoId = requestData["photoId"];
      std::string path = ThumbnailGenerator::getThumbnailPath(photoId);
      if (fs::exists(path)) {
        fs::remove(path);
      }
      // Trigger generation immediately
      PhotoMetadata photo = db_.getPhotoById(photoId);
      if (photo.id != -1) {
        if (ThumbnailGenerator::generateThumbnail(photo.originalPath, path)) {
          return json({{"success", true}, {"message", "Thumbnail regenerated"}})
              .dump();
        } else {
          return json({{"error", "Failed to regenerate"}}).dump();
        }
      } else {
        return json({{"error", "Photo not found"}}).dump();
      }
    }

    return json({{"error", "Invalid request. Received: " + requestData.dump()}})
        .dump();

  } catch (const std::exception &e) {
    LOG_ERROR("Error in handlePostRegenerateThumbnails: " +
              std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}
