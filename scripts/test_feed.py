import datetime as dt
import unittest
import update_feed as feed
class Filters(unittest.TestCase):
    def setUp(self):self.date=int(dt.datetime(2026,10,17,tzinfo=dt.timezone.utc).timestamp()*1000)
    def test_current_season_tags(self):
        self.assertTrue(feed.eligible('Bigg Boss Malayalam Season 8 Latest Review','', 'Creator',self.date))
        self.assertTrue(feed.eligible('#BBMS8Promo വേദാങ്കി vs ഐശ്വര്യ','', 'Asianet',self.date))
        self.assertTrue(feed.eligible('നിലീനയുടെ പുതിയ ട്വിസ്റ്റ്','#BBMS8 #BiggBossMalayalam', 'Creator',self.date))
    def test_old_and_wrong_language(self):
        self.assertFalse(feed.eligible('Bigg Boss Malayalam Season 7 recap','', 'Asianet',self.date))
        self.assertFalse(feed.eligible('Bigg Boss Tamil Season 8','', 'Tamil',self.date))
        self.assertFalse(feed.eligible('Bigg Boss Malayalam Season 8','', 'Creator',0))
    def test_sort_date(self):
        self.assertGreater(feed.published('1 hour ago'),feed.published('3 days ago'))
        self.assertEqual(feed.published('Coming soon'),0)
if __name__=='__main__':unittest.main()