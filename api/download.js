module.exports = async (req, res) => {
  const { guid } = req.query || {};
  if (!guid) {
    return res.status(400).send('Missing guid');
  }
  const redirectUrl = `https://elearn.uk.ac.ir/Downloader.aspx?guid=${encodeURIComponent(guid)}`;
  res.writeHead(302, { Location: redirectUrl });
  res.end();
};
