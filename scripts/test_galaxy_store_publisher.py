import unittest

from scripts.publish_galaxy_store import PublishError, _binary_metadata, _content_details, _update_payload


class GalaxyStorePublisherTests(unittest.TestCase):
    def setUp(self):
        self.details = {
            "contentId": "000012345678",
            "appTitle": "Counter POS",
            "longDescription": "Business point of sale and stock management.",
            "contentStatus": "FOR_SALE",
            "defaultLanguageCode": "ENG",
            "paid": "N",
            "publicationType": "01",
            "privatePolicyURLYN": "Y",
            "privatePolicyURL": "https://example.com/privacy",
            "usExportLaws": True,
            "sellCountryList": [{"countryCode": "KEN", "price": "0"}],
            "screenshots": [{"reuseYn": True}],
            "addLanguage": [],
            "binaryList": [
                {
                    "binarySeq": "2",
                    "versionCode": "43",
                    "versionName": "0.0.43",
                    "packageName": "com.alvinb.counterpos",
                    "gms": "Y",
                }
            ],
        }

    def test_selects_latest_matching_binary_and_carries_gms_and_device_mapping(self):
        self.details["binaryList"].append(
            {
                "binarySeq": "3",
                "versionCode": "44",
                "versionName": "0.0.44",
                "packageName": "com.alvinb.counterpos",
                "gms": "N",
            }
        )
        metadata = _binary_metadata(self.details)
        self.assertEqual(metadata["current_version_code"], 44)
        self.assertEqual(metadata["gms"], "N")
        self.assertEqual(metadata["binary_seq"], "3")

    def test_refuses_pending_or_non_sale_listing(self):
        self.details["contentStatus"] = "UPDATING"
        with self.assertRaisesRegex(PublishError, "not For Sale"):
            _binary_metadata(self.details)

    def test_refuses_wrong_package(self):
        self.details["binaryList"][0]["packageName"] = "com.example.other"
        with self.assertRaisesRegex(PublishError, "does not contain Android package"):
            _binary_metadata(self.details)

    def test_update_payload_preserves_existing_store_distribution_settings(self):
        payload = _update_payload(self.details, "000012345678")
        self.assertEqual(payload["contentId"], "000012345678")
        self.assertEqual(payload["appTitle"], "Counter POS")
        self.assertEqual(payload["longDescription"], "Business point of sale and stock management.")
        self.assertEqual(payload["publicationType"], "01")
        self.assertEqual(payload["defaultLanguageCode"], "ENG")
        self.assertEqual(payload["paid"], "N")
        self.assertEqual(payload["privatePolicyURLYN"], "Y")
        self.assertIs(payload["usExportLaws"], True)
        self.assertEqual(payload["sellCountryList"], "null")
        self.assertEqual(payload["screenshots"], "null")
        self.assertEqual(payload["addLanguage"], "null")

    def test_content_info_accepts_documented_array_response(self):
        self.assertEqual(_content_details([self.details]), self.details)

    def test_content_info_accepts_wrapped_object_response(self):
        self.assertEqual(_content_details({"data": self.details}), self.details)

    def test_missing_required_publication_setting_stops(self):
        del self.details["publicationType"]
        with self.assertRaisesRegex(PublishError, "publicationType"):
            _update_payload(self.details, "000012345678")


if __name__ == "__main__":
    unittest.main()
